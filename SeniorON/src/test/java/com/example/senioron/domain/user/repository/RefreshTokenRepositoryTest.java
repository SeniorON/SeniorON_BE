package com.example.senioron.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.senioron.domain.user.entity.RefreshToken;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.service.RefreshTokenService;
import com.example.senioron.global.jwt.JwtUtil;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class RefreshTokenRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        JwtUtil jwtUtil = new JwtUtil("12345678901234567890123456789012", 3600000L, 1209600000L);
        refreshTokenService = new RefreshTokenService(refreshTokenRepository, jwtUtil);
    }

    @Test
    void repeatedLoginWithSameUserAndDeviceIdentifierUpdatesOneRefreshTokenRow() {
        User user = saveUser();

        refreshTokenService.saveOrRotate(user, "device-1", "refresh-token-1");
        refreshTokenService.saveOrRotate(user, "device-1", "refresh-token-2");

        assertThat(refreshTokenRepository.count()).isEqualTo(1L);
        assertThat(refreshTokenRepository.findByUserAndDeviceIdentifier(user, "device-1"))
                .hasValueSatisfying(refreshToken ->
                        assertThat(refreshToken.getTokenHash())
                                .isEqualTo(refreshTokenService.hashToken("refresh-token-2"))
                );
    }

    @Test
    void nullDeviceIdentifierRemainsAccepted() {
        User user = saveUser();

        refreshTokenService.saveOrRotate(user, null, "refresh-token");

        assertThat(refreshTokenRepository.count()).isEqualTo(1L);
        assertThat(refreshTokenRepository.findByUserAndDeviceIdentifier(user, null)).isPresent();
    }

    @Test
    void databaseAllowsMultipleNullDeviceIdentifiersForSameUser() {
        User user = saveUser();

        refreshTokenRepository.saveAndFlush(createRefreshToken(user, null, "refresh-token-1"));
        refreshTokenRepository.saveAndFlush(createRefreshToken(user, null, "refresh-token-2"));

        assertThat(refreshTokenRepository.count()).isEqualTo(2L);
    }

    private User saveUser() {
        String unique = UUID.randomUUID().toString();

        return userRepository.saveAndFlush(
                User.builder()
                        .loginId("refresh-user-" + unique)
                        .email("refresh-" + unique + "@test.com")
                        .password("encoded-password")
                        .name("test")
                        .role(Role.PARENT)
                        .build()
        );
    }

    private RefreshToken createRefreshToken(User user, String deviceIdentifier, String token) {
        return RefreshToken.builder()
                .user(user)
                .deviceIdentifier(deviceIdentifier)
                .tokenHash(refreshTokenService.hashToken(token))
                .expiresAt(LocalDateTime.now().plusDays(14))
                .build();
    }
}
