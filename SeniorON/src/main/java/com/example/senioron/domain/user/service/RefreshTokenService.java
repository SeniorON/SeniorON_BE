package com.example.senioron.domain.user.service;

import com.example.senioron.domain.user.entity.RefreshToken;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.RefreshTokenRepository;
import com.example.senioron.global.jwt.JwtUtil;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;

    public void saveOrRotate(User user, String deviceIdentifier, String refreshToken) {
        String tokenHash = hashToken(refreshToken);
        LocalDateTime expiresAt = jwtUtil.getRefreshTokenExpiresAt();

        RefreshToken savedRefreshToken = refreshTokenRepository.findByUserAndDeviceIdentifier(user, deviceIdentifier)
                .orElseGet(() -> RefreshToken.builder()
                        .user(user)
                        .deviceIdentifier(deviceIdentifier)
                        .build());

        savedRefreshToken.rotate(tokenHash, expiresAt);
        refreshTokenRepository.save(savedRefreshToken);
    }

    public String hashToken(String token) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available.", e);
        }
    }
}
