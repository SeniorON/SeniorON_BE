package com.example.senioron.domain.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.example.senioron.domain.admin.entity.Admin;
import com.example.senioron.domain.admin.entity.AdminActivityType;
import com.example.senioron.domain.admin.entity.AdminInvitation;
import com.example.senioron.domain.admin.entity.AdminInvitationType;
import com.example.senioron.domain.admin.entity.AdminRole;
import com.example.senioron.domain.admin.entity.AdminStatus;
import com.example.senioron.domain.admin.repository.AdminActivityLogRepository;
import com.example.senioron.domain.admin.repository.AdminInvitationRepository;
import com.example.senioron.domain.admin.repository.AdminRepository;
import com.example.senioron.global.jwt.JwtUtil;
import com.example.senioron.global.jwt.TokenHashUtil;
import com.example.senioron.global.mail.EmailService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("local")
@TestPropertySource(properties = "spring.mail.username=test@senioron.site")
class AdminInvitationReinviteIntegrationTest {

    private MockMvc mockMvc;

    @Autowired private WebApplicationContext context;
    @Autowired private AdminRepository admins;
    @Autowired private AdminInvitationRepository invitations;
    @Autowired private AdminActivityLogRepository activityLogs;
    @Autowired private JwtUtil jwtUtil;
    @MockitoBean private EmailService emailService;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void invitedEmailIgnoringCaseReissuesLinkUpdatesProfileAndRecordsActivity() throws Exception {
        Admin actor = admins.saveAndFlush(admin(
                "reinvite-super@example.com", "슈퍼", AdminRole.SUPER, AdminStatus.ACTIVE));
        Admin invited = admins.saveAndFlush(admin(
                "reinvite@example.com", "기존 이름", AdminRole.MODERATOR, AdminStatus.INVITED));
        String oldToken = "old-reinvite-token";
        invitations.saveAndFlush(AdminInvitation.builder()
                .admin(invited)
                .type(AdminInvitationType.INVITE)
                .tokenHash(TokenHashUtil.hashHex(oldToken))
                .expiresAt(LocalDateTime.now().plusHours(1))
                .createdBy(actor)
                .build());
        clearInvocations(emailService);

        mockMvc.perform(post("/api/admin/admins/invitations")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtUtil.createAdminAccessToken(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"새 이름\",\"email\":\"REINVITE@EXAMPLE.COM\","
                                + "\"adminRole\":\"SUPER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("COMMON_201"))
                .andExpect(jsonPath("$.data.adminId").value(invited.getAdminId()))
                .andExpect(jsonPath("$.data.resent").value(true));

        mockMvc.perform(post("/api/admin/auth/invitations/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + oldToken + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ADMIN400"));
        mockMvc.perform(post("/api/admin/auth/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + oldToken + "\",\"password\":\"password1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ADMIN400"));

        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendAdminInvitation(eq("reinvite@example.com"), linkCaptor.capture());
        String newToken = linkCaptor.getValue().substring(
                linkCaptor.getValue().indexOf("token=") + "token=".length());
        mockMvc.perform(post("/api/admin/auth/invitations/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + newToken + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/auth/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + newToken + "\",\"password\":\"password1\"}"))
                .andExpect(status().isOk());

        Admin updated = admins.findById(invited.getAdminId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("새 이름");
        assertThat(updated.getAdminRole()).isEqualTo(AdminRole.SUPER);
        assertThat(updated.getStatus()).isEqualTo(AdminStatus.ACTIVE);
        assertThat(activityLogs.findAll()).anySatisfy(log -> {
            assertThat(log.getTargetId()).isEqualTo(invited.getAdminId());
            assertThat(log.getActivityType()).isEqualTo(AdminActivityType.ADMIN_INVITATION_RESENT);
        });
    }

    private Admin admin(String email, String name, AdminRole role, AdminStatus status) {
        return Admin.builder()
                .email(email)
                .password(status == AdminStatus.INVITED ? null : "encoded")
                .name(name)
                .adminRole(role)
                .status(status)
                .build();
    }
}
