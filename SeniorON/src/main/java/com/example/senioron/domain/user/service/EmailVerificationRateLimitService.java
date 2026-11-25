package com.example.senioron.domain.user.service;

import com.example.senioron.domain.user.entity.EmailVerificationRateLimit;
import com.example.senioron.domain.user.repository.EmailVerificationRateLimitRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class EmailVerificationRateLimitService {

    private static final Duration EMAIL_COOLDOWN = Duration.ofSeconds(60);
    private static final Duration EMAIL_SHORT_WINDOW = Duration.ofMinutes(10);
    private static final int EMAIL_SHORT_WINDOW_LIMIT = 3;
    private static final int EMAIL_DAILY_LIMIT = 20;
    private static final Duration IP_SHORT_WINDOW = Duration.ofMinutes(10);
    private static final int IP_SHORT_WINDOW_LIMIT = 10;

    private static final String EMAIL_COOLDOWN_PREFIX = "email-verification:email:cooldown:";
    private static final String EMAIL_SHORT_PREFIX = "email-verification:email:10m:";
    private static final String EMAIL_DAILY_PREFIX = "email-verification:email:day:";
    private static final String IP_SHORT_PREFIX = "email-verification:ip:10m:";

    private final EmailVerificationRateLimitRepository rateLimitRepository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Autowired
    public EmailVerificationRateLimitService(
            EmailVerificationRateLimitRepository rateLimitRepository,
            PlatformTransactionManager transactionManager
    ) {
        this(rateLimitRepository, transactionManager, Clock.systemDefaultZone());
    }

    EmailVerificationRateLimitService(
            EmailVerificationRateLimitRepository rateLimitRepository,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) {
        this.rateLimitRepository = rateLimitRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    public void checkAndRecord(String email, String clientIp) {
        try {
            checkAndRecordInNewTransaction(email, clientIp);
        } catch (DataIntegrityViolationException e) {
            checkAndRecordInNewTransaction(email, clientIp);
        }
    }

    private void checkAndRecordInNewTransaction(String email, String clientIp) {
        transactionTemplate.executeWithoutResult(status -> {
            LocalDateTime now = LocalDateTime.now(clock);
            String normalizedEmail = normalizeEmail(email);
            String normalizedIp = normalizeIp(clientIp);

            incrementOrThrow(
                    EMAIL_COOLDOWN_PREFIX + normalizedEmail,
                    now,
                    now,
                    now.plus(EMAIL_COOLDOWN),
                    1,
                    ErrorCode.EMAIL_VERIFICATION_COOLDOWN
            );
            incrementOrThrow(
                    EMAIL_SHORT_PREFIX + normalizedEmail,
                    now,
                    truncateToWindow(now, EMAIL_SHORT_WINDOW),
                    truncateToWindow(now, EMAIL_SHORT_WINDOW).plus(EMAIL_SHORT_WINDOW),
                    EMAIL_SHORT_WINDOW_LIMIT,
                    ErrorCode.EMAIL_VERIFICATION_RATE_LIMIT
            );
            incrementOrThrow(
                    EMAIL_DAILY_PREFIX + normalizedEmail + ":" + LocalDate.now(clock),
                    now,
                    LocalDate.now(clock).atStartOfDay(),
                    LocalDate.now(clock).plusDays(1).atStartOfDay(),
                    EMAIL_DAILY_LIMIT,
                    ErrorCode.EMAIL_VERIFICATION_DAILY_LIMIT
            );
            incrementOrThrow(
                    IP_SHORT_PREFIX + normalizedIp,
                    now,
                    truncateToWindow(now, IP_SHORT_WINDOW),
                    truncateToWindow(now, IP_SHORT_WINDOW).plus(IP_SHORT_WINDOW),
                    IP_SHORT_WINDOW_LIMIT,
                    ErrorCode.EMAIL_VERIFICATION_IP_RATE_LIMIT
            );
        });
    }

    private void incrementOrThrow(
            String key,
            LocalDateTime now,
            LocalDateTime windowStartedAt,
            LocalDateTime expiresAt,
            int limit,
            ErrorCode errorCode
    ) {
        Optional<EmailVerificationRateLimit> optionalRateLimit =
                rateLimitRepository.findByRateLimitKey(key);

        if (optionalRateLimit.isEmpty()) {
            rateLimitRepository.saveAndFlush(EmailVerificationRateLimit.builder()
                    .rateLimitKey(key)
                    .requestCount(1)
                    .windowStartedAt(windowStartedAt)
                    .expiresAt(expiresAt)
                    .build());
            return;
        }

        EmailVerificationRateLimit rateLimit = optionalRateLimit.get();
        if (rateLimit.isExpired(now)) {
            rateLimit.reset(windowStartedAt, expiresAt);
            return;
        }

        if (rateLimit.getRequestCount() >= limit) {
            throw new BusinessException(errorCode);
        }

        rateLimit.increment();
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeIp(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return "unknown";
        }

        return clientIp.trim();
    }

    private LocalDateTime truncateToWindow(LocalDateTime time, Duration window) {
        long windowSeconds = window.toSeconds();
        long secondOfDay = time.toLocalTime().toSecondOfDay();
        long windowStartSecond = (secondOfDay / windowSeconds) * windowSeconds;

        return LocalDateTime.of(time.toLocalDate(), LocalTime.ofSecondOfDay(windowStartSecond));
    }
}
