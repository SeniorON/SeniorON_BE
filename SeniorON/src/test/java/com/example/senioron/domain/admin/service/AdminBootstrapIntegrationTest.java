package com.example.senioron.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.senioron.domain.admin.entity.AdminInvitationType;
import com.example.senioron.domain.admin.entity.AdminRole;
import com.example.senioron.domain.admin.repository.AdminInvitationRepository;
import com.example.senioron.domain.admin.repository.AdminRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class AdminBootstrapIntegrationTest {
    @Autowired
    private AdminInvitationService service;
    @Autowired
    private AdminRepository admins;
    @Autowired
    private AdminInvitationRepository invitations;

    @Test
    void repeatedBootstrapKeepsOneSuperAndOneUsableInvitation() {
        service.ensureBootstrap("Bootstrap@Example.com");
        service.ensureBootstrap("bootstrap@example.com");

        var admin = admins.findByEmail("bootstrap@example.com").orElseThrow();
        assertThat(admin.getAdminRole()).isEqualTo(AdminRole.SUPER);
        assertThat(admins.findAll().stream()
                .filter(it -> it.getEmail().equals("bootstrap@example.com")))
                .hasSize(1);
        assertThat(invitations.findAllByAdminAndType(admin, AdminInvitationType.INVITE))
                .filteredOn(it -> it.isUsable(java.time.LocalDateTime.now()))
                .hasSize(1);
    }
}
