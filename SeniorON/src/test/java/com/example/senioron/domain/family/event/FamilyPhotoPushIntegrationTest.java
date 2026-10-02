package com.example.senioron.domain.family.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.senioron.domain.event.util.FcmSender;
import com.example.senioron.domain.family.service.FamilyPhotoPushService;
import com.example.senioron.domain.family.service.FamilyPhotoPushTargetService;
import com.example.senioron.global.config.FamilyPhotoAsyncConfig;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest(showSql = false)
@Import({FamilyPhotoAsyncConfig.class, FamilyPhotoSharedEventListener.class, FamilyPhotoPushService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Timeout(15)
class FamilyPhotoPushIntegrationTest {

    @Autowired private ApplicationEventPublisher events;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired @Qualifier("familyPhotoPushExecutor") private ThreadPoolTaskExecutor executor;
    @MockitoBean private FamilyPhotoPushTargetService targets;
    @MockitoBean private FcmSender fcmSender;

    @AfterEach
    void waitForWorkerCompletion() {
        await().atMost(Duration.ofSeconds(5)).until(() ->
                executor.getActiveCount() == 0 && executor.getQueueSize() == 0);
    }

    @Test
    void startsOnlyAfterCommitAndDoesNotWaitForSlowFcm() throws Exception {
        CountDownLatch sendStarted = new CountDownLatch(1);
        CountDownLatch releaseSend = new CountDownLatch(1);
        AtomicReference<String> workerThread = new AtomicReference<>();
        given(targets.load(10L)).willReturn(Optional.of(
                new FamilyPhotoPushTargetService.PushTarget("새 사진", List.of("token"))));
        given(fcmSender.sendData(anyString(), anyMap())).willAnswer(invocation -> {
            workerThread.set(Thread.currentThread().getName());
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            sendStarted.countDown();
            assertThat(releaseSend.await(5, TimeUnit.SECONDS)).isTrue();
            return true;
        });

        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                events.publishEvent(new FamilyPhotoSharedEvent(10L));
                verifyNoInteractions(targets, fcmSender);
            });
            // The request transaction returned while the FCM call is still blocked.
            assertThat(sendStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(releaseSend.getCount()).isEqualTo(1L);
            assertThat(workerThread.get()).startsWith("family-photo-push-");
        } finally {
            releaseSend.countDown();
        }
    }

    @Test
    void doesNotSendForRolledBackOrNonTransactionalEvents() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            events.publishEvent(new FamilyPhotoSharedEvent(10L));
            status.setRollbackOnly();
        });
        events.publishEvent(new FamilyPhotoSharedEvent(11L));
        verifyNoInteractions(targets, fcmSender);
    }

    @Test
    void rejectedWorkDoesNotFailCommitOrRunOnRequestThread() throws Exception {
        CountDownLatch occupied = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        Runnable blockingTask = () -> {
            occupied.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("worker release timed out");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        };
        try {
            executor.execute(blockingTask);
            executor.execute(blockingTask);
            assertThat(occupied.await(5, TimeUnit.SECONDS)).isTrue();
            for (int i = 0; i < 100; i++) {
                executor.execute(() -> { });
            }
            assertThat(executor.getQueueSize()).isEqualTo(100);

            assertThatCode(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                    events.publishEvent(new FamilyPhotoSharedEvent(10L))))
                    .doesNotThrowAnyException();
            verifyNoInteractions(targets, fcmSender);
        } finally {
            release.countDown();
        }
    }
}
