package com.example.senioron.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.senioron.domain.admin.dto.request.AdminUpdateRequest;
import com.example.senioron.domain.admin.entity.Admin;
import com.example.senioron.domain.admin.entity.AdminRole;
import com.example.senioron.domain.admin.entity.AdminStatus;
import com.example.senioron.domain.admin.repository.AdminActivityLogRepository;
import com.example.senioron.domain.admin.repository.AdminRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class AdminManagementServiceIntegrationTest {

    @Autowired private AdminManagementService service;
    @Autowired private AdminRepository admins;
    @Autowired private AdminActivityLogRepository activityLogs;
    @Autowired private EntityManager entityManager;

    private Admin lastSuper;
    private Admin staleActor;

    @BeforeEach
    void setUp() {
        // 다른 테스트가 커밋한 활성 SUPER를 이 트랜잭션 안에서만 제외해 대상이 마지막 활성 SUPER가 되게 한다.
        entityManager.createQuery("""
                        update Admin admin set admin.adminRole = :moderator
                        where admin.adminRole = :super and admin.status = :active
                        """)
                .setParameter("moderator", AdminRole.MODERATOR)
                .setParameter("super", AdminRole.SUPER)
                .setParameter("active", AdminStatus.ACTIVE)
                .executeUpdate();
        entityManager.clear();
        lastSuper = save(AdminRole.SUPER, AdminStatus.ACTIVE);
        // 요청 처리 중 다른 SUPER에게 먼저 강등된 요청자를 가정한다.
        staleActor = save(AdminRole.MODERATOR, AdminStatus.ACTIVE);
    }

    @Test
    void demotingLastActiveSuperIsRejected() {
        assertLastSuperRejected(new AdminUpdateRequest(AdminRole.MODERATOR, null));
    }

    @Test
    void suspendingLastActiveSuperIsRejected() {
        assertLastSuperRejected(new AdminUpdateRequest(null, false));
    }

    @Test
    void unchangedLastActiveSuperIsAllowed() {
        var response = service.update(
                staleActor, lastSuper.getAdminId(), new AdminUpdateRequest(AdminRole.SUPER, true), "127.0.0.1");

        assertThat(response.status()).isEqualTo(AdminStatus.ACTIVE);
        assertThat(response.adminRole()).isEqualTo(AdminRole.SUPER);
    }

    private void assertLastSuperRejected(AdminUpdateRequest request) {
        long logCount = activityLogs.count();

        assertThatThrownBy(() -> service.update(staleActor, lastSuper.getAdminId(), request, "127.0.0.1"))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.LAST_SUPER_ADMIN);
                    assertThat(e.getStatus().value()).isEqualTo(409);
                });

        Admin reloaded = admins.findById(lastSuper.getAdminId()).orElseThrow();
        assertThat(reloaded.getAdminRole()).isEqualTo(AdminRole.SUPER);
        assertThat(reloaded.getStatus()).isEqualTo(AdminStatus.ACTIVE);
        assertThat(activityLogs.count()).isEqualTo(logCount);
    }

    private Admin save(AdminRole role, AdminStatus status) {
        return admins.saveAndFlush(Admin.builder()
                .email("last-super-" + UUID.randomUUID() + "@example.com")
                .password("encoded")
                .name("관리자")
                .adminRole(role)
                .status(status)
                .build());
    }
}
