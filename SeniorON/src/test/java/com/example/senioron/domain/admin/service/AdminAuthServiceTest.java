package com.example.senioron.domain.admin.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import com.example.senioron.domain.admin.dto.request.*;
import com.example.senioron.domain.admin.entity.*;
import com.example.senioron.domain.admin.repository.*;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import com.example.senioron.global.jwt.JwtUtil;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AdminAuthServiceTest {
    private final AdminRepository adminRepository = mock(AdminRepository.class);
    private final AdminRefreshTokenRepository tokenRepository = mock(AdminRefreshTokenRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final JwtUtil jwt = new JwtUtil("12345678901234567890123456789012", 3600000, 1209600000,
            "admin-secret-key-12345678901234567890", 1800000, 86400000);
    private AdminAuthService service;

    @BeforeEach void setUp() { service = new AdminAuthService(adminRepository, tokenRepository, encoder, jwt); }

    @Test void missingEmailAndWrongPasswordReturnSameError() {
        given(adminRepository.findByEmail("missing@example.com")).willReturn(Optional.empty());
        Admin active = admin(AdminStatus.ACTIVE);
        given(adminRepository.findByEmail("admin@example.com")).willReturn(Optional.of(active));
        BusinessException missing = catchThrowableOfType(() -> service.login(new AdminLoginRequest("missing@example.com", "bad")), BusinessException.class);
        BusinessException wrong = catchThrowableOfType(() -> service.login(new AdminLoginRequest("admin@example.com", "bad")), BusinessException.class);
        assertThat(missing.getErrorCode()).isEqualTo(ErrorCode.INVALID_ADMIN_CREDENTIAL.getCode());
        assertThat(wrong.getErrorCode()).isEqualTo(ErrorCode.INVALID_ADMIN_CREDENTIAL.getCode());
    }

    @Test void inactiveAdminWithWrongPasswordReturnsCredentialError() {
        for (AdminStatus status : new AdminStatus[]{AdminStatus.INVITED, AdminStatus.SUSPENDED}) {
            given(adminRepository.findByEmail(status.name().toLowerCase() + "@example.com")).willReturn(Optional.of(admin(status)));
            assertThatThrownBy(() -> service.login(new AdminLoginRequest(status.name().toLowerCase() + "@example.com", "wrong")))
                    .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_ADMIN_CREDENTIAL));
        }
    }

    @Test void inactiveAdminWithCorrectPasswordReturnsNotActiveError() {
        Admin suspended = admin(AdminStatus.SUSPENDED);
        given(adminRepository.findByEmail("suspended@example.com")).willReturn(Optional.of(suspended));
        assertThatThrownBy(() -> service.login(new AdminLoginRequest("suspended@example.com", "password")))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.ADMIN_NOT_ACTIVE));
    }

    @Test void rotatedRefreshTokenRejectsOldToken() {
        Admin admin = admin(AdminStatus.ACTIVE);
        String old = jwt.createAdminRefreshToken(admin);
        AdminRefreshToken saved = AdminRefreshToken.builder().admin(admin).tokenHash(hash(old)).expiresAt(jwt.getAdminRefreshTokenExpiresAt()).build();
        given(adminRepository.findById(1L)).willReturn(Optional.of(admin));
        given(tokenRepository.findByTokenHashForUpdate(hash(old))).willReturn(Optional.of(saved));
        var rotated = service.refresh(new AdminRefreshRequest(old));
        given(tokenRepository.findByTokenHashForUpdate(hash(old))).willReturn(Optional.empty());
        assertThat(rotated.refreshToken()).isNotEqualTo(old);
        assertThatThrownBy(() -> service.refresh(new AdminRefreshRequest(old)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_ADMIN_REFRESH_TOKEN));
        verify(tokenRepository, times(2)).findByTokenHashForUpdate(hash(old));
    }

    @Test void logoutRevokesRefreshTokenAndPreventsRefresh() {
        Admin admin = admin(AdminStatus.ACTIVE);
        String token = jwt.createAdminRefreshToken(admin);
        AdminRefreshToken saved = AdminRefreshToken.builder().admin(admin).tokenHash(hash(token)).expiresAt(jwt.getAdminRefreshTokenExpiresAt()).build();
        given(tokenRepository.findByTokenHash(hash(token))).willReturn(Optional.of(saved));
        service.logout(token);
        given(adminRepository.findById(1L)).willReturn(Optional.of(admin));
        given(tokenRepository.findByTokenHashForUpdate(hash(token))).willReturn(Optional.of(saved));
        assertThatThrownBy(() -> service.refresh(new AdminRefreshRequest(token)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_ADMIN_REFRESH_TOKEN));
    }

    private Admin admin(AdminStatus status) { return Admin.builder().adminId(1L).email("admin@example.com").password(status == AdminStatus.INVITED ? null : encoder.encode("password")).name("관리자").adminRole(AdminRole.SUPER).status(status).build(); }
    private String hash(String value) { try { return java.util.Base64.getEncoder().encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); } catch (Exception e) { throw new RuntimeException(e); } }
}
