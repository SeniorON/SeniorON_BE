package com.example.senioron.global.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.senioron.global.storage.entity.S3DeletionTask;
import com.example.senioron.global.storage.repository.S3DeletionTaskRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
        "cloud.aws.region=ap-northeast-2",
        "cloud.aws.s3.bucket=test-bucket",
        "storage.s3-deletion.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:s3-deletion-worker;MODE=MariaDB;DB_CLOSE_DELAY=-1"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class S3DeletionServiceIntegrationTest {

    @Autowired private S3DeletionService service;
    @MockitoSpyBean private S3DeletionTaskRepository tasks;
    @MockitoBean private S3Service s3;
    @Autowired private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        tasks.deleteAllInBatch();
    }

    @Test
    void normalizesNullBlankAndDuplicateKeysAtQueueEntry() {
        enqueue(Arrays.asList(null, "", " ", "photo.jpg", "photo.jpg"));

        assertThat(tasks.findAll()).extracting(S3DeletionTask::getObjectKey)
                .containsExactly("photo.jpg");
        verifyNoInteractions(s3);
    }

    @Test
    void persistsOnlyFailedKeysAndRetriesThemAfterTheirDelay() {
        enqueue(List.of("success.jpg", "failed.jpg", "failed.jpg"));
        when(s3.deleteObjects(anyList())).thenReturn(Map.of("failed.jpg", "AccessDenied"));

        assertThat(service.processPendingBatch()).isEqualTo(1);

        assertThat(tasks.findAll()).singleElement().satisfies(task -> {
            assertThat(task.getObjectKey()).isEqualTo("failed.jpg");
            assertThat(task.getAttemptCount()).isEqualTo(1);
            assertThat(task.getLastError()).isEqualTo("AccessDenied");
            assertThat(task.getNextAttemptAt()).isAfter(LocalDateTime.now());
        });
        assertThat(service.processPendingBatch()).isZero();
        verify(s3, times(1)).deleteObjects(anyList());

        makeTasksDue();
        doReturn(Map.of()).when(s3).deleteObjects(anyList());
        assertThat(service.processPendingBatch()).isEqualTo(1);
        verify(s3).deleteObjects(List.of("failed.jpg"));
        assertThat(tasks.count()).isZero();
    }

    @Test
    void requestFailureKeepsAllTasksAndRecordsRetryInformation() {
        enqueue(List.of("first.jpg", "second.jpg"));
        when(s3.deleteObjects(anyList())).thenThrow(new IllegalStateException("S3 unavailable"));

        assertThat(service.processPendingBatch()).isZero();

        assertThat(tasks.findAll()).hasSize(2).allSatisfy(task -> {
            assertThat(task.getAttemptCount()).isEqualTo(1);
            assertThat(task.getLastError()).contains("S3 unavailable");
            assertThat(task.getNextAttemptAt()).isAfter(LocalDateTime.now());
        });
        makeTasksDue();
        doReturn(Map.of()).when(s3).deleteObjects(anyList());
        assertThat(service.processPendingBatch()).isEqualTo(2);
        assertThat(tasks.count()).isZero();
    }

    @Test
    void databaseFailureAfterS3SuccessLeavesTasksForSafeReplay() {
        enqueue(List.of("photo.jpg"));
        when(s3.deleteObjects(anyList())).thenReturn(Map.of());
        doThrow(new DataAccessResourceFailureException("DB unavailable"))
                .when(tasks).deleteAllInBatch(anyList());

        assertThatThrownBy(service::processPendingBatch)
                .isInstanceOf(DataAccessResourceFailureException.class);
        reset(tasks);
        assertThat(tasks.count()).isEqualTo(1);

        assertThat(service.processPendingBatch()).isEqualTo(1);
        verify(s3, times(2)).deleteObjects(List.of("photo.jpg"));
        assertThat(tasks.count()).isZero();
    }

    @Test
    void requiresCallerTransactionAndRollsBackEnqueuedTasks() {
        assertThatThrownBy(() -> service.enqueue(List.of("photo.jpg")))
                .isInstanceOf(IllegalTransactionStateException.class);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            service.enqueue(List.of("photo.jpg"));
            status.setRollbackOnly();
        });

        assertThat(tasks.count()).isZero();
        verifyNoInteractions(s3);
    }

    @Test
    void concurrentWorkersSerializeTheSameTaskAndLeaveNoWorkBehind() throws Exception {
        enqueue(List.of("photo.jpg"));
        CountDownLatch deleting = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        AtomicInteger activeCalls = new AtomicInteger();
        AtomicInteger maxActiveCalls = new AtomicInteger();
        when(s3.deleteObjects(anyList())).thenAnswer(invocation -> {
            maxActiveCalls.accumulateAndGet(activeCalls.incrementAndGet(), Math::max);
            deleting.countDown();
            try {
                assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                return Map.of();
            } finally {
                activeCalls.decrementAndGet();
            }
        });
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> service.processPendingBatch());
            assertThat(deleting.await(5, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> {
                secondStarted.countDown();
                return service.processPendingBatch();
            });
            assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> second.get(200, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            release.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
            assertThat(maxActiveCalls.get()).isEqualTo(1);
            assertThat(tasks.count()).isZero();
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void enqueuesNewWorkWhileAnExistingBatchWaitsForS3() throws Exception {
        enqueue(List.of("existing.jpg"));
        CountDownLatch deleting = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(s3.deleteObjects(anyList())).thenAnswer(invocation -> {
            deleting.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return Map.of();
        });
        var executor = Executors.newFixedThreadPool(2);
        try {
            var worker = executor.submit(() -> service.processPendingBatch());
            assertThat(deleting.await(5, TimeUnit.SECONDS)).isTrue();
            // S3 응답을 풀기 전에도 다른 탈퇴 요청은 작업을 등록하고 커밋할 수 있어야 한다.
            executor.submit(() -> enqueue(List.of("new.jpg"))).get(3, TimeUnit.SECONDS);
            release.countDown();
            assertThat(worker.get(10, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(tasks.findAll()).extracting(S3DeletionTask::getObjectKey)
                    .containsExactly("new.jpg");
            assertThat(service.processPendingBatch()).isEqualTo(1);
            assertThat(tasks.count()).isZero();
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void retryBackoffIsCappedAndErrorFitsTheDatabaseColumn() {
        LocalDateTime now = LocalDateTime.now();
        S3DeletionTask task = S3DeletionTask.pending("photo.jpg", now);
        task.recordFailure("x".repeat(1000), now);
        assertThat(task.getNextAttemptAt()).isEqualTo(now.plusMinutes(1));
        for (int i = 0; i < 15; i++) {
            task.recordFailure("x".repeat(1000), now);
        }
        assertThat(task.getNextAttemptAt()).isEqualTo(now.plusHours(1));
        assertThat(task.getLastError()).hasSize(512);
    }

    private void enqueue(List<String> keys) {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> service.enqueue(keys));
    }

    private void makeTasksDue() {
        tasks.findAll().forEach(task -> {
            ReflectionTestUtils.setField(task, "nextAttemptAt", LocalDateTime.now().minusMinutes(1));
            tasks.saveAndFlush(task);
        });
    }
}
