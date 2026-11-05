package com.example.senioron.domain.senior.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.senioron.domain.event.util.FcmSender;
import com.example.senioron.domain.senior.service.SeniorWithdrawalPushService;
import com.example.senioron.global.config.SeniorWithdrawalAsyncConfig;
import java.time.Duration;
import java.util.List;
import java.util.Map;
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
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest(showSql = false)
@Import({
        SeniorWithdrawalAsyncConfig.class,
        SeniorWithdrawalNoticeCreatedEventListener.class,
        SeniorWithdrawalPushService.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Timeout(15)
class SeniorWithdrawalPushIntegrationTest {

    @Autowired private ApplicationEventPublisher eventPublisher;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired
    @Qualifier("seniorWithdrawalPushExecutor")
    private ThreadPoolTaskExecutor executor;
    @MockitoBean private FcmSender fcmSender;

    @AfterEach
    void waitForWorkerCompletion() {
        await().atMost(Duration.ofSeconds(5)).until(() ->
                executor.getActiveCount() == 0 && executor.getQueueSize() == 0);
    }

    @Test
    void sendsOnlyAfterCommitOnDedicatedAsyncWorker() throws Exception {
        CountDownLatch sent = new CountDownLatch(1);
        AtomicReference<String> workerThread = new AtomicReference<>();
        given(fcmSender.sendData(anyString(), anyMap())).willAnswer(invocation -> {
            workerThread.set(Thread.currentThread().getName());
            sent.countDown();
            return true;
        });
        SeniorWithdrawalNoticeCreatedEvent event = event(List.of("child-token"));

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            eventPublisher.publishEvent(event);
            verifyNoInteractions(fcmSender);
        });

        assertThat(sent.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(workerThread.get()).startsWith("senior-withdrawal-push-");
        verify(fcmSender).sendData("child-token", Map.of(
                "type", "SENIOR_WITHDRAWAL",
                "title", "시니어 탈퇴 안내",
                "body", "김영희님이 시니어온 서비스를 탈퇴하셨습니다. 앱에서 탈퇴 안내를 확인해 주세요.",
                "withdrawalNoticeId", "15",
                "seniorId", "10",
                "familyId", "1"
        ));
    }

    @Test
    void doesNotSendAfterRollback() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.executeWithoutResult(status -> {
            eventPublisher.publishEvent(event(List.of("child-token")));
            status.setRollbackOnly();
        });

        await().during(Duration.ofMillis(300)).atMost(Duration.ofSeconds(1))
                .untilAsserted(() -> verifyNoInteractions(fcmSender));
    }

    @Test
    void fcmFailureDoesNotEscapeAsyncDispatch() throws Exception {
        CountDownLatch attempted = new CountDownLatch(2);
        given(fcmSender.sendData(anyString(), anyMap())).willAnswer(invocation -> {
            attempted.countDown();
            if ("bad-token".equals(invocation.getArgument(0))) {
                throw new IllegalStateException("fcm unavailable");
            }
            return true;
        });

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                eventPublisher.publishEvent(event(List.of("bad-token", "good-token"))));

        assertThat(attempted.await(5, TimeUnit.SECONDS)).isTrue();
        verify(fcmSender).sendData("bad-token", payload());
        verify(fcmSender).sendData("good-token", payload());
    }

    private SeniorWithdrawalNoticeCreatedEvent event(List<String> tokens) {
        return new SeniorWithdrawalNoticeCreatedEvent(
                15L,
                10L,
                1L,
                "김영희",
                List.of(new SeniorWithdrawalNoticeCreatedEvent.PushTarget(20L, tokens))
        );
    }

    private Map<String, String> payload() {
        return Map.of(
                "type", "SENIOR_WITHDRAWAL",
                "title", "시니어 탈퇴 안내",
                "body", "김영희님이 시니어온 서비스를 탈퇴하셨습니다. 앱에서 탈퇴 안내를 확인해 주세요.",
                "withdrawalNoticeId", "15",
                "seniorId", "10",
                "familyId", "1"
        );
    }
}
