package com.example.senioron.domain.user.service;

import com.example.senioron.domain.user.entity.RefreshToken;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.RefreshTokenRepository;
import com.example.senioron.global.jwt.JwtUtil;
import com.example.senioron.global.jwt.TokenHashUtil;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RefreshTokenService {

    public static final Duration IDLE_TIMEOUT = Duration.ofHours(1);
    private static final Duration ACTIVITY_TOUCH_INTERVAL = Duration.ofMinutes(1);

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;
    private final Clock clock;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            JwtUtil jwtUtil
    ) {
        this(refreshTokenRepository, jwtUtil, Clock.systemDefaultZone());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            JwtUtil jwtUtil,
            Clock clock
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtUtil = jwtUtil;
        this.clock = clock;
    }

    public RefreshToken saveOrRotate(
            User user,
            String deviceIdentifier,
            String refreshToken,
            boolean keepLoggedIn
    ) {
        String tokenHash = TokenHashUtil.hash(refreshToken);
        LocalDateTime expiresAt = jwtUtil.getRefreshTokenExpiresAt();
        LocalDateTime now = now();

        return refreshTokenRepository.findByUserAndDeviceIdentifier(user, deviceIdentifier)
                .map(savedRefreshToken -> startNewSessionAndSave(
                        savedRefreshToken, tokenHash, expiresAt, keepLoggedIn, now))
                .orElseGet(() -> saveNewOrRetry(
                        user, deviceIdentifier, tokenHash, expiresAt, keepLoggedIn, now));
    }

    public RefreshToken saveOrRotate(User user, String deviceIdentifier, String refreshToken) {
        return saveOrRotate(user, deviceIdentifier, refreshToken, false);
    }

    public void revoke(User user, String deviceIdentifier) {
        refreshTokenRepository.findByUserAndDeviceIdentifier(user, deviceIdentifier)
                .ifPresent(refreshToken -> {
                    refreshToken.revoke();
                    refreshTokenRepository.saveAndFlush(refreshToken);
                });
    }

    /**
     * Access Token의 sessionId로 기기별 로그인 세션을 검증하고 활동을 기록한다.
     * keepLoggedIn=false 세션은 이 메서드에서 유휴 만료를 강제한다.
     */
    public boolean authenticateAndRecordActivity(User user, String sessionId) {
        RefreshToken refreshToken = refreshTokenRepository.findBySessionId(sessionId)
                .orElse(null);

        if (refreshToken == null
                || !Objects.equals(refreshToken.getUser().getUsersId(), user.getUsersId())) {
            return false;
        }

        LocalDateTime now = now();
        if (refreshToken.isRevoked() || refreshToken.isExpired(now)) {
            return false;
        }

        if (refreshToken.isIdleExpired(now, IDLE_TIMEOUT.toSeconds())) {
            refreshToken.revoke();
            refreshTokenRepository.saveAndFlush(refreshToken);
            return false;
        }

        recordActivityIfNeeded(refreshToken, now);
        return true;
    }

    public boolean isIdleExpired(RefreshToken refreshToken) {
        return refreshToken.isIdleExpired(now(), IDLE_TIMEOUT.toSeconds());
    }

    public boolean isExpired(RefreshToken refreshToken) {
        return refreshToken.isExpired(now());
    }

    public void rotateAfterRefresh(RefreshToken refreshToken, String newRefreshToken) {
        refreshToken.rotate(
                hashToken(newRefreshToken),
                jwtUtil.getRefreshTokenExpiresAt(),
                now()
        );
    }

    private void recordActivityIfNeeded(RefreshToken refreshToken, LocalDateTime now) {
        if (refreshToken.isKeepLoggedIn()) {
            return;
        }

        // 마지막 기록이 1분 이내면 기존 시각을 유지해 불필요한 UPDATE를 줄인다.
        // 유휴 만료는 인증 전에 검사하므로, 실제 활동이 1시간을 넘겨 되살아나는 일은 없다.
        if (refreshToken.getLastActiveAt().plus(ACTIVITY_TOUCH_INTERVAL).isAfter(now)) {
            return;
        }

        refreshToken.updateLastActiveAt(now);
    }

    private RefreshToken saveNewOrRetry(
            User user,
            String deviceIdentifier,
            String tokenHash,
            LocalDateTime expiresAt,
            boolean keepLoggedIn,
            LocalDateTime now
    ) {
        RefreshToken newRefreshToken = RefreshToken.builder()
                .user(user)
                .deviceIdentifier(deviceIdentifier)
                .build();

        try {
            return startNewSessionAndSave(
                    newRefreshToken, tokenHash, expiresAt, keepLoggedIn, now);
        } catch (DataIntegrityViolationException e) {
            if (deviceIdentifier == null) {
                throw e;
            }

            RefreshToken savedRefreshToken = refreshTokenRepository.findByUserAndDeviceIdentifier(user, deviceIdentifier)
                    .orElseThrow(() -> e);
            return startNewSessionAndSave(
                    savedRefreshToken, tokenHash, expiresAt, keepLoggedIn, now);
        }
    }

    private RefreshToken startNewSessionAndSave(
            RefreshToken refreshToken,
            String tokenHash,
            LocalDateTime expiresAt,
            boolean keepLoggedIn,
            LocalDateTime now
    ) {
        refreshToken.startNewSession(tokenHash, expiresAt, keepLoggedIn, now);
        refreshTokenRepository.saveAndFlush(refreshToken);
        return refreshToken;
    }

    public String hashToken(String token) {
        return TokenHashUtil.hash(token);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
