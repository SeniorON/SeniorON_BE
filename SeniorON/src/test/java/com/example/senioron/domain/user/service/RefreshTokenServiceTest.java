package com.example.senioron.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.senioron.domain.user.entity.RefreshToken;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.RefreshTokenRepository;
import com.example.senioron.global.jwt.JwtUtil;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class RefreshTokenServiceTest {

    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final JwtUtil jwtUtil = new JwtUtil(
            "12345678901234567890123456789012", 3600000L, 1209600000L,
            "admin-test-secret-key-123456789012", 1800000L, 86400000L
    );
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-17T00:00:00Z"));
    private final RefreshTokenService refreshTokenService = new RefreshTokenService(refreshTokenRepository, jwtUtil, clock);

    @Test
    void saveOrRotateRetriesWhenConcurrentInsertCreatesSameUserAndDeviceIdentifier() {
        User user = createUser();
        String deviceIdentifier = "device-1";
        RefreshToken existingRefreshToken = RefreshToken.builder()
                .user(user)
                .deviceIdentifier(deviceIdentifier)
                .tokenHash(refreshTokenService.hashToken("old-refresh-token"))
                .build();

        given(refreshTokenRepository.findByUserAndDeviceIdentifier(user, deviceIdentifier))
                .willReturn(Optional.empty(), Optional.of(existingRefreshToken));
        given(refreshTokenRepository.saveAndFlush(any(RefreshToken.class)))
                .willThrow(new DataIntegrityViolationException("duplicate user device refresh token"))
                .willAnswer(invocation -> invocation.getArgument(0));

        refreshTokenService.saveOrRotate(user, deviceIdentifier, "new-refresh-token");

        assertThat(existingRefreshToken.getTokenHash())
                .isEqualTo(refreshTokenService.hashToken("new-refresh-token"));
        verify(refreshTokenRepository, times(2)).findByUserAndDeviceIdentifier(user, deviceIdentifier);
        verify(refreshTokenRepository, times(2)).saveAndFlush(any(RefreshToken.class));
    }

    @Test
    void recordsActivityForActiveNonPersistentSession() {
        User user = createUser();
        RefreshToken refreshToken = refreshToken(user, "session-1", false, clock.now().minusMinutes(2));
        given(refreshTokenRepository.findBySessionId("session-1"))
                .willReturn(Optional.of(refreshToken));

        assertThat(refreshTokenService.authenticateAndRecordActivity(user, "session-1")).isTrue();
        assertThat(refreshToken.getLastActiveAt()).isEqualTo(clock.now());
    }

    @Test
    void expiresOnlyIdleSessionWhenSameUserHasMultipleDeviceSessions() {
        User user = createUser();
        RefreshToken activeDevice = refreshToken(user, "active-device", false, clock.now().minusMinutes(10));
        RefreshToken idleDevice = refreshToken(user, "idle-device", false, clock.now().minusHours(1));
        given(refreshTokenRepository.findBySessionId("active-device"))
                .willReturn(Optional.of(activeDevice));
        given(refreshTokenRepository.findBySessionId("idle-device"))
                .willReturn(Optional.of(idleDevice));

        assertThat(refreshTokenService.authenticateAndRecordActivity(user, "active-device")).isTrue();
        assertThat(refreshTokenService.authenticateAndRecordActivity(user, "idle-device")).isFalse();
        assertThat(activeDevice.isRevoked()).isFalse();
        assertThat(idleDevice.isRevoked()).isTrue();
        verify(refreshTokenRepository).saveAndFlush(idleDevice);
    }

    @Test
    void persistentSessionDoesNotExpireAfterOneHourOfInactivity() {
        User user = createUser();
        RefreshToken refreshToken = refreshToken(user, "persistent-session", true, clock.now().minusHours(2));
        given(refreshTokenRepository.findBySessionId("persistent-session"))
                .willReturn(Optional.of(refreshToken));

        assertThat(refreshTokenService.authenticateAndRecordActivity(user, "persistent-session")).isTrue();
        assertThat(refreshToken.isRevoked()).isFalse();
    }

    private RefreshToken refreshToken(
            User user,
            String sessionId,
            boolean keepLoggedIn,
            LocalDateTime lastActiveAt
    ) {
        return RefreshToken.builder()
                .user(user)
                .deviceIdentifier(sessionId)
                .sessionId(sessionId)
                .tokenHash(refreshTokenService.hashToken("token-" + sessionId))
                .expiresAt(clock.now().plusDays(14))
                .keepLoggedIn(keepLoggedIn)
                .lastActiveAt(lastActiveAt)
                .build();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private LocalDateTime now() {
            return LocalDateTime.now(this);
        }
    }

    private User createUser() {
        return User.builder()
                .usersId(1L)
                .loginId("testId")
                .email("test@example.com")
                .password("encoded-password")
                .name("test")
                .role(Role.PARENT)
                .build();
    }
}
