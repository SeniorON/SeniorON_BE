package com.example.senioron.domain.admin.service;

import com.example.senioron.domain.admin.dto.request.AdminLoginRequest;
import com.example.senioron.domain.admin.dto.request.AdminRefreshRequest;
import com.example.senioron.domain.admin.dto.response.AdminLoginResponse;
import com.example.senioron.domain.admin.dto.response.AdminMeResponse;
import com.example.senioron.domain.admin.dto.response.AdminTokenResponse;
import com.example.senioron.domain.admin.entity.Admin;
import com.example.senioron.domain.admin.entity.AdminRefreshToken;
import com.example.senioron.domain.admin.repository.AdminRefreshTokenRepository;
import com.example.senioron.domain.admin.repository.AdminRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import com.example.senioron.global.jwt.JwtUtil;
import com.example.senioron.global.jwt.TokenHashUtil;
import io.jsonwebtoken.JwtException;
import java.time.LocalDateTime;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAuthService {

    private final AdminRepository adminRepository;
    private final AdminRefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional
    public AdminLoginResponse login(AdminLoginRequest request) {
        Admin admin = adminRepository.findByEmail(
                        request.email().trim().toLowerCase(Locale.ROOT)
                )
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_ADMIN_CREDENTIAL
                ));

        if (admin.getPassword() == null
                || !passwordEncoder.matches(request.password(), admin.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_ADMIN_CREDENTIAL);
        }
        validateActive(admin);

        admin.recordLogin(LocalDateTime.now());
        String accessToken = jwtUtil.createAdminAccessToken(admin);
        String refreshToken = jwtUtil.createAdminRefreshToken(admin);
        refreshTokenRepository.save(AdminRefreshToken.builder()
                .admin(admin)
                .tokenHash(TokenHashUtil.hash(refreshToken))
                .expiresAt(jwtUtil.getAdminRefreshTokenExpiresAt())
                .build());

        return AdminLoginResponse.of(accessToken, refreshToken, admin);
    }

    @Transactional
    public AdminTokenResponse refresh(AdminRefreshRequest request) {
        Admin admin = parseRefreshTokenAdmin(request.refreshToken());
        AdminRefreshToken savedToken = refreshTokenRepository
                .findByTokenHashForUpdate(TokenHashUtil.hash(request.refreshToken()))
                .filter(token -> !token.isRevoked())
                .filter(token -> !token.isExpired(LocalDateTime.now()))
                .filter(token -> token.getAdmin().getAdminId().equals(admin.getAdminId()))
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_ADMIN_REFRESH_TOKEN
                ));

        validateActive(admin);

        String accessToken = jwtUtil.createAdminAccessToken(admin);
        String refreshToken = jwtUtil.createAdminRefreshToken(admin);
        savedToken.rotate(
                TokenHashUtil.hash(refreshToken),
                jwtUtil.getAdminRefreshTokenExpiresAt()
        );

        return new AdminTokenResponse(accessToken, refreshToken);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.findByTokenHash(TokenHashUtil.hash(refreshToken))
                .ifPresent(AdminRefreshToken::revoke);
    }

    public AdminMeResponse me(Admin admin) {
        return AdminMeResponse.from(admin);
    }

    private Admin parseRefreshTokenAdmin(String refreshToken) {
        try {
            var claims = jwtUtil.parseAdminClaims(refreshToken);
            if (!jwtUtil.isAdminRefreshToken(claims)) {
                throw new JwtException("invalid type");
            }

            return adminRepository.findById(jwtUtil.getAdminId(claims))
                    .orElseThrow(() -> new JwtException("admin missing"));
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_ADMIN_REFRESH_TOKEN);
        }
    }

    private void validateActive(Admin admin) {
        if (!admin.isActive()) {
            throw new BusinessException(ErrorCode.ADMIN_NOT_ACTIVE);
        }
    }
}
