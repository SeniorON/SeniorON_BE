package com.example.senioron.domain.user.service;

import com.example.senioron.domain.user.entity.RefreshToken;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.RefreshTokenRepository;
import com.example.senioron.global.jwt.JwtUtil;
import com.example.senioron.global.jwt.TokenHashUtil;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;

    public void saveOrRotate(User user, String deviceIdentifier, String refreshToken) {
        String tokenHash = TokenHashUtil.hash(refreshToken);
        LocalDateTime expiresAt = jwtUtil.getRefreshTokenExpiresAt();

        refreshTokenRepository.findByUserAndDeviceIdentifier(user, deviceIdentifier)
                .ifPresentOrElse(
                        savedRefreshToken -> rotateAndSave(savedRefreshToken, tokenHash, expiresAt),
                        () -> saveNewOrRetry(user, deviceIdentifier, tokenHash, expiresAt)
                );
    }

    public void revoke(User user, String deviceIdentifier) {
        refreshTokenRepository.findByUserAndDeviceIdentifier(user, deviceIdentifier)
                .ifPresent(refreshToken -> {
                    refreshToken.revoke();
                    refreshTokenRepository.saveAndFlush(refreshToken);
                });
    }

    private void saveNewOrRetry(
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
            rotateAndSave(newRefreshToken, tokenHash, expiresAt);
        } catch (DataIntegrityViolationException e) {
            if (deviceIdentifier == null) {
                throw e;
            }

            RefreshToken savedRefreshToken = refreshTokenRepository.findByUserAndDeviceIdentifier(user, deviceIdentifier)
                    .orElseThrow(() -> e);
            rotateAndSave(savedRefreshToken, tokenHash, expiresAt);
        }
    }

    private void rotateAndSave(RefreshToken refreshToken, String tokenHash, LocalDateTime expiresAt) {
        refreshToken.rotate(tokenHash, expiresAt);
        refreshTokenRepository.saveAndFlush(refreshToken);
    }

    public String hashToken(String token) {
        return TokenHashUtil.hash(token);
    }
}
