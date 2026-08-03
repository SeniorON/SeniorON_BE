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
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class RefreshTokenServiceTest {

    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final JwtUtil jwtUtil = new JwtUtil("12345678901234567890123456789012", 3600000L, 1209600000L);
    private final RefreshTokenService refreshTokenService = new RefreshTokenService(refreshTokenRepository, jwtUtil);

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
