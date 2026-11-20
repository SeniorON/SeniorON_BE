package com.example.senioron.domain.user.service;

import com.example.senioron.domain.user.entity.RefreshToken;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.RefreshTokenRepository;
import com.example.senioron.global.jwt.JwtUtil;
import com.example.senioron.global.jwt.TokenHashUtil;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RefreshTokenService {

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

    public RefreshToken saveOrRotate(User user, String deviceIdentifier, String refreshToken) {
        String tokenHash = TokenHashUtil.hash(refreshToken);
        LocalDateTime expiresAt = jwtUtil.getRefreshTokenExpiresAt();

        return refreshTokenRepository.findByUserAndDeviceIdentifier(user, deviceIdentifier)
                .map(savedRefreshToken -> startNewSessionAndSave(
                        savedRefreshToken, tokenHash, expiresAt))
                .orElseGet(() -> saveNewOrRetry(
                        user, deviceIdentifier, tokenHash, expiresAt));
    }

    public void revoke(User user, String deviceIdentifier) {
        refreshTokenRepository.findByUserAndDeviceIdentifier(user, deviceIdentifier)
                .ifPresent(refreshToken -> {
                    refreshToken.revoke();
                    refreshTokenRepository.saveAndFlush(refreshToken);
                });
    }

    /** Access Token의 sessionId로 기기별 로그인 세션의 유효성을 검증한다. */
    public boolean isValidSession(User user, String sessionId) {
        RefreshToken refreshToken = refreshTokenRepository.findBySessionId(sessionId)
                .orElse(null);

        if (refreshToken == null
                || !Objects.equals(refreshToken.getUser().getUsersId(), user.getUsersId())) {
            return false;
        }

        LocalDateTime now = now();
        return !refreshToken.isRevoked() && !refreshToken.isExpired(now);
    }

    public boolean isExpired(RefreshToken refreshToken) {
        return refreshToken.isExpired(now());
    }

    public void rotateAfterRefresh(RefreshToken refreshToken, String newRefreshToken) {
        refreshToken.rotate(
                hashToken(newRefreshToken),
                jwtUtil.getRefreshTokenExpiresAt()
        );
    }

    private RefreshToken saveNewOrRetry(
            User user,
            String deviceIdentifier,
            String tokenHash,
            LocalDateTime expiresAt
    ) {
        RefreshToken newRefreshToken = RefreshToken.builder()
                .user(user)
                .deviceIdentifier(deviceIdentifier)
                .build();

        try {
            return startNewSessionAndSave(
                    newRefreshToken, tokenHash, expiresAt);
        } catch (DataIntegrityViolationException e) {
            if (deviceIdentifier == null) {
                throw e;
            }

            RefreshToken savedRefreshToken = refreshTokenRepository.findByUserAndDeviceIdentifier(user, deviceIdentifier)
                    .orElseThrow(() -> e);
            return startNewSessionAndSave(
                    savedRefreshToken, tokenHash, expiresAt);
        }
    }

    private RefreshToken startNewSessionAndSave(
            RefreshToken refreshToken,
            String tokenHash,
            LocalDateTime expiresAt
    ) {
        refreshToken.startNewSession(tokenHash, expiresAt);
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
