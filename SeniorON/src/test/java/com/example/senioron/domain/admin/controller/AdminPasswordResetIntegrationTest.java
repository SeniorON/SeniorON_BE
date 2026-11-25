package com.example.senioron.domain.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.example.senioron.domain.admin.entity.Admin;
import com.example.senioron.domain.admin.entity.AdminActivityTargetType;
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
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("local")
@TestPropertySource(properties = "spring.mail.username=test@senioron.site")
class AdminPasswordResetIntegrationTest {

    private static final String CLIENT_IP = "203.0.113.20";
    private static final String OLD_PASSWORD = "oldpassword1";
    private static final String NEW_PASSWORD = "newpassword2";

    private MockMvc mockMvc;

    @Autowired private WebApplicationContext context;
    @Autowired private AdminRepository admins;
    @Autowired private AdminInvitationRepository invitations;
    @Autowired private AdminActivityLogRepository activityLogs;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoBean private EmailService emailService;

    private Admin superAdmin;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        superAdmin = save("슈퍼", AdminRole.SUPER, AdminStatus.ACTIVE);
        clearInvocations(emailService);
    }

    @Test
    void superSendsOneHourResetLinkInvalidatesPreviousResetLinkAndRecordsActivity() throws Exception {
        Admin target = save("재설정 대상", AdminRole.MODERATOR, AdminStatus.ACTIVE);
        String previousToken = "previous-reset-" + UUID.randomUUID();
        invitations.saveAndFlush(AdminInvitation.builder()
                .admin(target)
                .type(AdminInvitationType.RESET)
                .tokenHash(TokenHashUtil.hashHex(previousToken))
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .createdBy(superAdmin)
                .build());

        LocalDateTime before = LocalDateTime.now();
        requestReset(superAdmin, target.getAdminId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON_200"));
        LocalDateTime after = LocalDateTime.now();

        List<AdminInvitation> resetLinks = invitations.findAllByAdminAndType(target, AdminInvitationType.RESET);
        assertThat(resetLinks).hasSize(2);
        List<AdminInvitation> usable = resetLinks.stream()
                .filter(invitation -> invitation.isUsable(LocalDateTime.now()))
                .toList();
        assertThat(usable).hasSize(1);
        assertThat(usable.get(0).getExpiresAt())
                .isAfterOrEqualTo(before.plusHours(1))
                .isBeforeOrEqualTo(after.plusHours(1));
        assertThat(resetLinks).filteredOn(invitation ->
                        invitation.getTokenHash().equals(TokenHashUtil.hashHex(previousToken)))
                .singleElement()
                .satisfies(invitation -> assertThat(invitation.getInvalidatedAt()).isNotNull());
        assertThat(invitations.findAllByAdminAndType(target, AdminInvitationType.INVITE)).isEmpty();

        assertThat(activityLogs.findAll()).filteredOn(log -> target.getAdminId().equals(log.getTargetId()))
                .singleElement()
                .satisfies(log -> {
                    assertThat(log.getActivityType()).isEqualTo(AdminActivityType.ADMIN_PASSWORD_RESET_SENT);
                    assertThat(log.getTargetType()).isEqualTo(AdminActivityTargetType.ADMIN);
                    assertThat(log.getTargetName()).isEqualTo("재설정 대상");
                    assertThat(log.getIpAddress()).isEqualTo(CLIENT_IP);
                });

        verify(emailService).sendAdminPasswordReset(eq(target.getEmail()), anyString());
        verify(emailService, never()).sendAdminInvitation(anyString(), anyString());
        verifyStatus(previousToken, 400, "ADMIN400");
    }

    @Test
    void resetLinkVerifiesAsResetAndAcceptChangesPasswordAndRevokesRefreshTokens() throws Exception {
        Admin target = save("비밀번호 분실", AdminRole.MODERATOR, AdminStatus.ACTIVE);
        String loginBody = login(target.getEmail(), OLD_PASSWORD)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String oldRefreshToken = JsonPath.read(loginBody, "$.data.refreshToken");

        requestReset(superAdmin, target.getAdminId()).andExpect(status().isOk());
        String token = sentResetToken(target.getEmail());

        mockMvc.perform(post("/api/admin/auth/invitations/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("RESET"))
                .andExpect(jsonPath("$.data.email").value(target.getEmail()));
        accept(token, NEW_PASSWORD).andExpect(status().isOk());

        login(target.getEmail(), NEW_PASSWORD).andExpect(status().isOk());
        login(target.getEmail(), OLD_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ADMIN401"));
        mockMvc.perform(post("/api/admin/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + oldRefreshToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ADMIN401_1"));
        assertThat(admins.findById(target.getAdminId()).orElseThrow().getStatus())
                .isEqualTo(AdminStatus.ACTIVE);

        verifyStatus(token, 400, "ADMIN400");
        accept(token, "anotherpass3")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ADMIN400"));
    }

    @Test
    void expiredResetLinkAfterOneHourIsRejected() throws Exception {
        Admin target = save("만료 대상", AdminRole.MODERATOR, AdminStatus.ACTIVE);
        requestReset(superAdmin, target.getAdminId()).andExpect(status().isOk());
        String token = sentResetToken(target.getEmail());

        jdbcTemplate.update(
                "update admin_invitation set created_at = ?, expires_at = ? where token_hash = ?",
                LocalDateTime.now().minusHours(1).minusSeconds(1),
                LocalDateTime.now().minusSeconds(1),
                TokenHashUtil.hashHex(token));

        verifyStatus(token, 400, "ADMIN400");
        accept(token, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ADMIN400"));
        login(target.getEmail(), OLD_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void resetLinkBecomesInvalidWhenTargetIsSuspended() throws Exception {
        Admin target = save("정지 예정", AdminRole.MODERATOR, AdminStatus.ACTIVE);
        requestReset(superAdmin, target.getAdminId()).andExpect(status().isOk());
        String token = sentResetToken(target.getEmail());
        jdbcTemplate.update("update admin set status = 'SUSPENDED' where admin_id = ?", target.getAdminId());

        verifyStatus(token, 400, "ADMIN400");
        accept(token, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ADMIN400"));
    }

    @Test
    void moderatorCannotSendPasswordReset() throws Exception {
        Admin moderator = save("모더레이터", AdminRole.MODERATOR, AdminStatus.ACTIVE);
        Admin target = save("대상", AdminRole.MODERATOR, AdminStatus.ACTIVE);

        requestReset(moderator, target.getAdminId()).andExpect(status().isForbidden());

        assertThat(invitations.findAllByAdminAndType(target, AdminInvitationType.RESET)).isEmpty();
        verify(emailService, never()).sendAdminPasswordReset(anyString(), anyString());
    }

    @Test
    void selfMissingAndNonActiveTargetsAreRejected() throws Exception {
        requestReset(superAdmin, superAdmin.getAdminId())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ADMIN400_2"));
        requestReset(superAdmin, Long.MAX_VALUE)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ADMIN404"));

        for (AdminStatus status : List.of(AdminStatus.INVITED, AdminStatus.SUSPENDED)) {
            Admin target = save("상태 " + status, AdminRole.MODERATOR, status);
            requestReset(superAdmin, target.getAdminId())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ADMIN409_1"));
            assertThat(invitations.findAllByAdminAndType(target, AdminInvitationType.RESET)).isEmpty();
        }
        assertThat(invitations.findAllByAdminAndType(superAdmin, AdminInvitationType.RESET)).isEmpty();
        verify(emailService, never()).sendAdminPasswordReset(anyString(), anyString());
    }

    @Test
    void invitationLinkStillExpiresAfter48Hours() throws Exception {
        String email = "invite-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        LocalDateTime before = LocalDateTime.now();
        mockMvc.perform(post("/api/admin/admins/invitations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtUtil.createAdminAccessToken(superAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"초대\",\"email\":\"" + email + "\",\"adminRole\":\"MODERATOR\"}"))
                .andExpect(status().isCreated());
        LocalDateTime after = LocalDateTime.now();

        Admin invited = admins.findByEmail(email).orElseThrow();
        assertThat(invitations.findAllByAdminAndType(invited, AdminInvitationType.INVITE))
                .singleElement()
                .satisfies(invitation -> assertThat(invitation.getExpiresAt())
                        .isAfterOrEqualTo(before.plusHours(48))
                        .isBeforeOrEqualTo(after.plusHours(48)));
        assertThat(invitations.findAllByAdminAndType(invited, AdminInvitationType.RESET)).isEmpty();
        verify(emailService).sendAdminInvitation(eq(email), anyString());
        verify(emailService, never()).sendAdminPasswordReset(anyString(), anyString());
    }

    private ResultActions requestReset(Admin actor, Long adminId) throws Exception {
        return mockMvc.perform(post("/api/admin/admins/" + adminId + "/password-reset")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtUtil.createAdminAccessToken(actor))
                .header("X-Real-IP", CLIENT_IP));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private ResultActions accept(String token, String password) throws Exception {
        return mockMvc.perform(post("/api/admin/auth/invitations/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"password\":\"" + password + "\"}"));
    }

    private void verifyStatus(String token, int httpStatus, String code) throws Exception {
        mockMvc.perform(post("/api/admin/auth/invitations/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.code").value(code));
    }

    private String sentResetToken(String email) {
        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendAdminPasswordReset(eq(email), linkCaptor.capture());
        String link = linkCaptor.getValue();
        assertThat(link).contains("/invitation?token=");
        return link.substring(link.indexOf("token=") + "token=".length());
    }

    private Admin save(String name, AdminRole role, AdminStatus status) {
        return admins.saveAndFlush(Admin.builder()
                .email("reset-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "@example.com")
                .password(status == AdminStatus.INVITED ? null : passwordEncoder.encode(OLD_PASSWORD))
                .name(name)
                .adminRole(role)
                .status(status)
                .build());
    }
}
