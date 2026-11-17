package com.example.senioron.domain.user.entity;

import com.example.senioron.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "refresh_tokens",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_refresh_tokens_user_device",
                        columnNames = {"users_id", "device_identifier"}
                )
        },
        indexes = {
                @Index(name = "idx_refresh_tokens_expires_at", columnList = "expires_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class RefreshToken extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refresh_token_id")
    private Long refreshTokenId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "users_id", nullable = false)
    private User user;

    @Column(name = "device_identifier")
    private String deviceIdentifier;

    @Column(name = "session_id", nullable = false, unique = true, length = 36)
    @Builder.Default
    private String sessionId = UUID.randomUUID().toString();

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "keep_logged_in", nullable = false)
    @Builder.Default
    private boolean keepLoggedIn = false;

    @Column(name = "last_active_at", nullable = false)
    @Builder.Default
    private LocalDateTime lastActiveAt = LocalDateTime.now();

    @Column(nullable = false)
    @Builder.Default
    private boolean revoked = false;

    public void startNewSession(
            String tokenHash,
            LocalDateTime expiresAt,
            boolean keepLoggedIn,
            LocalDateTime now
    ) {
        this.sessionId = UUID.randomUUID().toString();
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.keepLoggedIn = keepLoggedIn;
        this.lastActiveAt = now;
        this.revoked = false;
    }

    public void rotate(String tokenHash, LocalDateTime expiresAt, LocalDateTime now) {
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.lastActiveAt = now;
        this.revoked = false;
    }

    public void updateLastActiveAt(LocalDateTime now) {
        this.lastActiveAt = now;
    }

    public void revoke() {
        this.revoked = true;
    }

    public boolean isExpired(LocalDateTime now) {
        return expiresAt.isBefore(now) || expiresAt.isEqual(now);
    }

    public boolean isIdleExpired(LocalDateTime now, long idleTimeoutSeconds) {
        return !keepLoggedIn
                && !lastActiveAt.plusSeconds(idleTimeoutSeconds).isAfter(now);
    }
}
