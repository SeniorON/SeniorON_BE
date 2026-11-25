package com.example.senioron.domain.user.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.senioron.domain.admin.entity.Admin;
import com.example.senioron.domain.admin.entity.AdminRefreshToken;
import com.example.senioron.domain.admin.entity.AdminRole;
import com.example.senioron.domain.admin.entity.AdminStatus;
import com.example.senioron.domain.admin.repository.AdminRefreshTokenRepository;
import com.example.senioron.domain.admin.repository.AdminRepository;
import com.example.senioron.domain.user.repository.RefreshTokenRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class RefreshTokenCleanupSchedulerTest {

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private AdminRefreshTokenRepository adminTokenRepository;

    @Autowired
    private RefreshTokenRepository userTokenRepository;

    @Test
    void removesExpiredAndRevokedAdminTokensButKeepsValidToken() {
        Admin admin = adminRepository.save(Admin.builder()
                .email("cleanup@example.com")
                .password("encoded")
                .name("관리자")
                .adminRole(AdminRole.SUPER)
                .status(AdminStatus.ACTIVE)
                .build());
        AdminRefreshToken expired = token(admin, "expired", LocalDateTime.now().minusDays(1));
        AdminRefreshToken revoked = token(admin, "revoked", LocalDateTime.now().plusDays(1));
        revoked.revoke();
        AdminRefreshToken valid = token(admin, "valid", LocalDateTime.now().plusDays(1));
        adminTokenRepository.saveAndFlush(expired);
        adminTokenRepository.saveAndFlush(revoked);
        adminTokenRepository.saveAndFlush(valid);

        new RefreshTokenCleanupScheduler(userTokenRepository, adminTokenRepository)
                .deleteExpiredRefreshTokens();

        assertThat(adminTokenRepository.findByTokenHash("expired")).isEmpty();
        assertThat(adminTokenRepository.findByTokenHash("revoked")).isEmpty();
        assertThat(adminTokenRepository.findByTokenHash("valid")).isPresent();
    }

    private AdminRefreshToken token(
            Admin admin,
            String hash,
            LocalDateTime expiresAt
    ) {
        return AdminRefreshToken.builder()
                .admin(admin)
                .tokenHash(hash)
                .expiresAt(expiresAt)
                .build();
    }
}
