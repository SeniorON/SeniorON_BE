package com.example.senioron.domain.user.repository;

import com.example.senioron.domain.user.entity.EmailVerificationRateLimit;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface EmailVerificationRateLimitRepository
        extends JpaRepository<EmailVerificationRateLimit, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<EmailVerificationRateLimit> findByRateLimitKey(String rateLimitKey);
}
