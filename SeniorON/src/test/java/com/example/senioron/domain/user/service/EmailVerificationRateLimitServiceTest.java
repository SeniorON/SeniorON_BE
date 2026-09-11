package com.example.senioron.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.senioron.domain.user.entity.EmailVerificationRateLimit;
import com.example.senioron.domain.user.repository.EmailVerificationRateLimitRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EmailVerificationRateLimitServiceTest {

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final String EMAIL = "rate-limit@example.com";
    private static final String IP = "127.0.0.1";

    @Autowired
    private EmailVerificationRateLimitRepository rateLimitRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        rateLimitRepository.deleteAll();
    }

    @Test
    void firstRequestSucceeds() {
        EmailVerificationRateLimitService service = createService("2026-09-11T00:00:00Z");

        service.checkAndRecord(EMAIL, IP);

        assertThat(rateLimitRepository.count()).isEqualTo(4);
    }

    @Test
    void sameEmailWithinSixtySecondsThrowsCooldown() {
        EmailVerificationRateLimitService service = createService("2026-09-11T00:00:00Z");
        service.checkAndRecord(EMAIL, IP);

        assertThatThrownBy(() -> service.checkAndRecord(EMAIL, IP))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.EMAIL_VERIFICATION_COOLDOWN);
    }

    @Test
    void sameEmailAfterSixtySecondsSucceeds() {
        createService("2026-09-11T00:00:00Z").checkAndRecord(EMAIL, IP);

        createService("2026-09-11T00:01:00Z").checkAndRecord(EMAIL, IP);

        assertThat(totalCount("email-verification:email:10m:" + EMAIL)).isEqualTo(2);
    }

    @Test
    void sameEmailAllowsThreeRequestsWithinTenMinutes() {
        createService("2026-09-11T00:00:00Z").checkAndRecord(EMAIL, IP);
        createService("2026-09-11T00:01:00Z").checkAndRecord(EMAIL, IP);
        createService("2026-09-11T00:02:00Z").checkAndRecord(EMAIL, IP);

        assertThat(totalCount("email-verification:email:10m:" + EMAIL)).isEqualTo(3);
    }

    @Test
    void sameEmailFourthRequestWithinTenMinutesThrowsRateLimit() {
        createService("2026-09-11T00:00:00Z").checkAndRecord(EMAIL, IP);
        createService("2026-09-11T00:01:00Z").checkAndRecord(EMAIL, IP);
        createService("2026-09-11T00:02:00Z").checkAndRecord(EMAIL, IP);

        assertThatThrownBy(() -> createService("2026-09-11T00:03:00Z").checkAndRecord(EMAIL, IP))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.EMAIL_VERIFICATION_RATE_LIMIT);
    }

    @Test
    void sameEmailDailyLimitThrowsAfterTwentyRequests() {
        for (int i = 0; i < 20; i++) {
            createService(LocalDateTime.of(2026, 9, 11, 0, 0).plusMinutes(i * 11L))
                    .checkAndRecord(EMAIL, "10.0.0." + i);
        }

        assertThatThrownBy(() -> createService(LocalDateTime.of(2026, 9, 11, 3, 40))
                .checkAndRecord(EMAIL, "10.0.0.20"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.EMAIL_VERIFICATION_DAILY_LIMIT);
    }

    @Test
    void sameIpThrowsAfterTenRequestsWithinTenMinutesEvenWithDifferentEmails() {
        for (int i = 0; i < 10; i++) {
            String instant = "2026-09-11T00:%02d:00Z".formatted(i);
            createService(instant).checkAndRecord("user" + i + "@example.com", IP);
        }

        assertThatThrownBy(() -> createService("2026-09-11T00:09:30Z").checkAndRecord("extra@example.com", IP))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.EMAIL_VERIFICATION_IP_RATE_LIMIT);
    }

    @Test
    void concurrentRequestsDoNotExceedLimit() throws InterruptedException {
        int threadCount = 12;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            int index = i;
            executorService.submit(() -> {
                ready.countDown();
                await(start);
                try {
                    createService("2026-09-11T00:0" + (index % 3) + ":00Z")
                            .checkAndRecord(EMAIL, "192.168.0." + index);
                    successCount.incrementAndGet();
                } catch (BusinessException
                         | DataIntegrityViolationException
                         | ObjectOptimisticLockingFailureException ignored) {
                    // Expected for requests rejected by the rate-limit race.
                }
            });
        }

        assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        executorService.shutdown();
        assertThat(executorService.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        assertThat(successCount.get()).isLessThanOrEqualTo(3);
    }

    private EmailVerificationRateLimitService createService(String instant) {
        Clock clock = Clock.fixed(Instant.parse(instant), ZONE_ID);
        return new EmailVerificationRateLimitService(rateLimitRepository, transactionManager, clock);
    }

    private EmailVerificationRateLimitService createService(LocalDateTime dateTime) {
        Clock clock = Clock.fixed(dateTime.atZone(ZONE_ID).toInstant(), ZONE_ID);
        return new EmailVerificationRateLimitService(rateLimitRepository, transactionManager, clock);
    }

    private int totalCount(String key) {
        return rateLimitRepository.findById(key)
                .map(EmailVerificationRateLimit::getRequestCount)
                .orElse(0);
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
