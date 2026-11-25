package com.example.senioron.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class EmailVerificationRateLimit {

    @Id
    @Column(length = 191)
    private String rateLimitKey;

    @Column(nullable = false)
    private int requestCount;

    @Column(nullable = false)
    private LocalDateTime windowStartedAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    public boolean isExpired(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }

    public void reset(LocalDateTime windowStartedAt, LocalDateTime expiresAt) {
        this.requestCount = 1;
        this.windowStartedAt = windowStartedAt;
        this.expiresAt = expiresAt;
    }

    public void increment() {
        this.requestCount++;
    }
}
