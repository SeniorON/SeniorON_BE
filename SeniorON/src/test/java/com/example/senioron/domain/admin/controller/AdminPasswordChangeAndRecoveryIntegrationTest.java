package com.example.senioron.domain.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.example.senioron.domain.admin.entity.Admin;
import com.example.senioron.domain.admin.entity.AdminActivityLog;
import com.example.senioron.domain.admin.entity.AdminActivityTargetType;
import com.example.senioron.domain.admin.entity.AdminActivityType;
import com.example.senioron.domain.admin.entity.AdminInvitation;
import com.example.senioron.domain.admin.entity.AdminInvitationType;
import com.example.senioron.domain.admin.entity.AdminRole;
import com.example.senioron.domain.admin.entity.AdminStatus;
import com.example.senioron.domain.admin.repository.AdminActivityLogRepository;
import com.example.senioron.domain.admin.repository.AdminInvitationRepository;
import com.example.senioron.domain.admin.repository.AdminRepository;
import com.example.senioron.domain.admin.service.AdminActivityLogService;
import com.example.senioron.global.jwt.JwtUtil;
import com.example.senioron.global.jwt.TokenHashUtil;
import com.example.senioron.global.mail.EmailService;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("local")
@TestPropertySource(properties = "spring.mail.username=test@senioron.site")
class AdminPasswordChangeAndRecoveryIntegrationTest {

    private static final String OLD_PASSWORD = "oldpassword1";
    private static final String NEW_PASSWORD = "newpassword2";
    // 64자 이내지만 UTF-8 74바이트라 BCrypt 72바이트 제한을 넘는다.
    private static final String OVER_72_BYTES_PASSWORD = "a1" + "가".repeat(24);

    private MockMvc mockMvc;
    private String clientIp;

    @Autowired private WebApplicationContext context;
    @Autowired private AdminRepository admins;
    @Autowired private AdminInvitationRepository invitations;
    @Autowired private AdminActivityLogRepository activityLogs;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private PasswordEncoder passwordEncoder;
    @MockitoBean private EmailService emailService;
    @MockitoSpyBean private AdminActivityLogService activityLogService;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        // IP 기준 발송 제한이 테스트 간에 공유되지 않도록 테스트마다 다른 IP를 사용한다.
        ThreadLocalRandom random = ThreadLocalRandom.current();
        clientIp = "10." + random.nextInt(256) + "." + random.nextInt(256) + "." + random.nextInt(1, 255);
        clearInvocations(emailService);
    }

    @Test
    void changePasswordSwitchesLoginPasswordRevokesRefreshTokensAndRecordsActivity() throws Exception {
        Admin admin = save("변경 대상", AdminStatus.ACTIVE);
        String oldRefreshToken = JsonPath.read(login(admin.getEmail(), OLD_PASSWORD)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.data.refreshToken");

        changePassword(admin, OLD_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON_200"))
                .andExpect(jsonPath("$.data").doesNotExist());

        login(admin.getEmail(), NEW_PASSWORD).andExpect(status().isOk());
        login(admin.getEmail(), OLD_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ADMIN401"));
        refresh(oldRefreshToken)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ADMIN401_1"));

        assertThat(logsTargeting(admin))
                .singleElement()
                .satisfies(log -> {
                    assertThat(log.getActivityType()).isEqualTo(AdminActivityType.ADMIN_PASSWORD_CHANGED);
                    assertThat(log.getAdmin().getAdminId()).isEqualTo(admin.getAdminId());
                    assertThat(log.getTargetType()).isEqualTo(AdminActivityTargetType.ADMIN);
                    assertThat(log.getIpAddress()).isEqualTo(clientIp);
                });
    }

    @Test
    void changePasswordAllowsSamePassword() throws Exception {
        Admin admin = save("같은 비밀번호", AdminStatus.ACTIVE);

        changePassword(admin, OLD_PASSWORD, OLD_PASSWORD).andExpect(status().isOk());

        login(admin.getEmail(), OLD_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void changePasswordRejectsWrongCurrentPasswordAndRuleViolationWithoutChanging() throws Exception {
        Admin admin = save("검증 대상", AdminStatus.ACTIVE);
        String refreshToken = JsonPath.read(login(admin.getEmail(), OLD_PASSWORD)
                .andReturn().getResponse().getContentAsString(), "$.data.refreshToken");

        changePassword(admin, "wrongpassword9", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ADMIN400_3"));
        for (String invalid : List.of("short1", "onlyletters", "1234567890", "a1".repeat(33))) {
            changePassword(admin, OLD_PASSWORD, invalid)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("ADMIN400_1"));
        }
        mockMvc.perform(patch("/api/admin/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtUtil.createAdminAccessToken(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isBadRequest());

        login(admin.getEmail(), OLD_PASSWORD).andExpect(status().isOk());
        refresh(refreshToken).andExpect(status().isOk());
        assertThat(logsTargeting(admin)).isEmpty();
    }

    @Test
    void changePasswordRequiresToken() throws Exception {
        mockMvc.perform(patch("/api/admin/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + OLD_PASSWORD + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passwordResetWithoutTokenIssuesOneHourResetLinkForActiveAdminWithCaseInsensitiveEmail() throws Exception {
        Admin admin = save("비밀번호 분실", AdminStatus.ACTIVE);
        String previousToken = "previous-reset-" + UUID.randomUUID();
        invitations.saveAndFlush(AdminInvitation.builder()
                .admin(admin)
                .type(AdminInvitationType.RESET)
                .tokenHash(TokenHashUtil.hashHex(previousToken))
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .createdBy(admin)
                .build());

        LocalDateTime before = LocalDateTime.now();
        requestReset(admin.getEmail().toUpperCase(Locale.ROOT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON_200"))
                .andExpect(jsonPath("$.data").doesNotExist());
        LocalDateTime after = LocalDateTime.now();

        List<AdminInvitation> resetLinks = invitations.findAllByAdminAndType(admin, AdminInvitationType.RESET);
        assertThat(resetLinks).hasSize(2);
        assertThat(resetLinks).filteredOn(invitation -> invitation.isUsable(LocalDateTime.now()))
                .singleElement()
                .satisfies(invitation -> assertThat(invitation.getExpiresAt())
                        .isAfterOrEqualTo(before.plusHours(1))
                        .isBeforeOrEqualTo(after.plusHours(1)));
        assertThat(resetLinks).filteredOn(invitation ->
                        invitation.getTokenHash().equals(TokenHashUtil.hashHex(previousToken)))
                .singleElement()
                .satisfies(invitation -> assertThat(invitation.getInvalidatedAt()).isNotNull());
        sentResetToken(admin.getEmail());

        assertThat(logsTargeting(admin))
                .singleElement()
                .satisfies(log -> {
                    assertThat(log.getActivityType()).isEqualTo(AdminActivityType.ADMIN_PASSWORD_RESET_REQUESTED);
                    assertThat(log.getAdmin().getAdminId()).isEqualTo(admin.getAdminId());
                    assertThat(log.getTargetType()).isEqualTo(AdminActivityTargetType.ADMIN);
                    assertThat(log.getIpAddress()).isEqualTo(clientIp);
                });
    }

    @Test
    void passwordResetReturnsSameResponseWithoutLinkOrMailForMissingInvitedAndSuspended() throws Exception {
        Admin active = save("활성", AdminStatus.ACTIVE);
        String activeBody = requestReset(active.getEmail())
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        clearInvocations(emailService);

        Admin invited = save("초대됨", AdminStatus.INVITED);
        Admin suspended = save("정지됨", AdminStatus.SUSPENDED);
        for (String email : List.of(randomEmail(), invited.getEmail(), suspended.getEmail())) {
            String body = requestReset(email)
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).isEqualTo(activeBody);
        }

        for (Admin admin : List.of(invited, suspended)) {
            assertThat(invitations.findAllByAdminAndType(admin, AdminInvitationType.RESET)).isEmpty();
            assertThat(logsTargeting(admin)).isEmpty();
        }
        verify(emailService, never()).sendAdminPasswordReset(anyString(), anyString());
    }

    @Test
    void passwordResetIsRateLimitedPerEmailEvenForUnknownEmail() throws Exception {
        String unknown = randomEmail();
        requestReset(unknown).andExpect(status().isOk());
        requestReset(unknown.toUpperCase(Locale.ROOT))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("EMAIL429_1"));

        Admin admin = save("제한 대상", AdminStatus.ACTIVE);
        requestReset(admin.getEmail()).andExpect(status().isOk());
        requestReset(admin.getEmail())
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("EMAIL429_1"));

        assertThat(invitations.findAllByAdminAndType(admin, AdminInvitationType.RESET)).hasSize(1);
        verify(emailService, times(1)).sendAdminPasswordReset(eq(admin.getEmail()), anyString());
    }

    @Test
    void passwordResetIsRateLimitedPerIp() throws Exception {
        for (int i = 0; i < 10; i++) {
            requestReset(randomEmail()).andExpect(status().isOk());
        }
        requestReset(randomEmail())
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("EMAIL429_4"));
    }

    @Test
    void passwordResetDoesNotSendMailOrKeepLinkWhenTransactionRollsBackButKeepsRateLimitRecord() throws Exception {
        Admin admin = save("롤백 대상", AdminStatus.ACTIVE);
        doThrow(new IllegalStateException("활동 기록 실패"))
                .when(activityLogService).record(any(), eq(AdminActivityType.ADMIN_PASSWORD_RESET_REQUESTED),
                        any(), any(), any(), any(), any());

        requestReset(admin.getEmail()).andExpect(status().isInternalServerError());

        assertThat(invitations.findAllByAdminAndType(admin, AdminInvitationType.RESET)).isEmpty();
        assertThat(logsTargeting(admin)).isEmpty();
        verify(emailService, never()).sendAdminPasswordReset(anyString(), anyString());
        // 발송 제한은 재설정 트랜잭션과 분리되어 롤백되지 않는다.
        requestReset(admin.getEmail())
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("EMAIL429_1"));
    }

    @Test
    void resetLinkFromPasswordResetCanBeAcceptedAndUsedToLogin() throws Exception {
        Admin admin = save("링크 사용", AdminStatus.ACTIVE);
        String oldRefreshToken = JsonPath.read(login(admin.getEmail(), OLD_PASSWORD)
                .andReturn().getResponse().getContentAsString(), "$.data.refreshToken");

        requestReset(admin.getEmail()).andExpect(status().isOk());
        String token = sentResetToken(admin.getEmail());

        mockMvc.perform(post("/api/admin/auth/invitations/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("RESET"));
        mockMvc.perform(post("/api/admin/auth/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"password\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isOk());

        login(admin.getEmail(), NEW_PASSWORD).andExpect(status().isOk());
        login(admin.getEmail(), OLD_PASSWORD).andExpect(status().isUnauthorized());
        refresh(oldRefreshToken).andExpect(status().isUnauthorized());
    }

    @Test
    void passwordOver72BytesIsRejectedAsPolicyViolationOnChangeAndAccept() throws Exception {
        Admin active = save("바이트 초과 변경", AdminStatus.ACTIVE);
        changePassword(active, OLD_PASSWORD, OVER_72_BYTES_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ADMIN400_1"));
        login(active.getEmail(), OLD_PASSWORD).andExpect(status().isOk());

        String resetToken = issueLink(active, AdminInvitationType.RESET);
        accept(resetToken, OVER_72_BYTES_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ADMIN400_1"));

        Admin invited = save("바이트 초과 초대", AdminStatus.INVITED);
        String inviteToken = issueLink(invited, AdminInvitationType.INVITE);
        accept(inviteToken, OVER_72_BYTES_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ADMIN400_1"));

        login(active.getEmail(), OLD_PASSWORD).andExpect(status().isOk());
        assertThat(admins.findById(invited.getAdminId()).orElseThrow().getStatus()).isEqualTo(AdminStatus.INVITED);
    }

    @Test
    void changePasswordInvalidatesPreviouslyIssuedResetLinksButKeepsInviteLinks() throws Exception {
        Admin admin = save("재설정 링크 무효화", AdminStatus.ACTIVE);
        String resetToken = issueLink(admin, AdminInvitationType.RESET);
        String inviteToken = issueLink(admin, AdminInvitationType.INVITE);

        changePassword(admin, OLD_PASSWORD, NEW_PASSWORD).andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/auth/invitations/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + resetToken + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ADMIN400"));
        accept(resetToken, "anotherpassword3")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ADMIN400"));

        login(admin.getEmail(), NEW_PASSWORD).andExpect(status().isOk());
        assertThat(invitations.findByTokenHash(TokenHashUtil.hashHex(resetToken)).orElseThrow()
                .getInvalidatedAt()).isNotNull();
        assertThat(invitations.findByTokenHash(TokenHashUtil.hashHex(inviteToken)).orElseThrow()
                .getInvalidatedAt()).isNull();
    }

    private String issueLink(Admin admin, AdminInvitationType type) {
        String token = type.name().toLowerCase(Locale.ROOT) + "-" + UUID.randomUUID();
        invitations.saveAndFlush(AdminInvitation.builder()
                .admin(admin)
                .type(type)
                .tokenHash(TokenHashUtil.hashHex(token))
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .createdBy(admin)
                .build());
        return token;
    }

    private ResultActions accept(String token, String password) throws Exception {
        return mockMvc.perform(post("/api/admin/auth/invitations/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"password\":\"" + password + "\"}"));
    }

    private ResultActions changePassword(Admin admin, String currentPassword, String newPassword) throws Exception {
        return mockMvc.perform(patch("/api/admin/me/password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtUtil.createAdminAccessToken(admin))
                .header("X-Real-IP", clientIp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + currentPassword
                        + "\",\"newPassword\":\"" + newPassword + "\"}"));
    }

    private ResultActions requestReset(String email) throws Exception {
        return mockMvc.perform(post("/api/admin/auth/password-reset")
                .header("X-Real-IP", clientIp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/admin/auth/token/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"));
    }

    private String sentResetToken(String email) {
        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendAdminPasswordReset(eq(email), linkCaptor.capture());
        String link = linkCaptor.getValue();
        assertThat(link).contains("/invitation?token=");
        return link.substring(link.indexOf("token=") + "token=".length());
    }

    private List<AdminActivityLog> logsTargeting(Admin admin) {
        return activityLogs.findAll().stream()
                .filter(log -> admin.getAdminId().equals(log.getTargetId()))
                .toList();
    }

    private String randomEmail() {
        return "recovery-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "@example.com";
    }

    private Admin save(String name, AdminStatus status) {
        return admins.saveAndFlush(Admin.builder()
                .email(randomEmail())
                .password(status == AdminStatus.INVITED ? null : passwordEncoder.encode(OLD_PASSWORD))
                .name(name)
                .adminRole(AdminRole.MODERATOR)
                .status(status)
                .build());
    }
}
