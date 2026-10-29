package com.example.senioron.domain.admin.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.example.senioron.domain.admin.entity.Admin;
import com.example.senioron.domain.admin.entity.AdminRole;
import com.example.senioron.domain.admin.entity.AdminStatus;
import com.example.senioron.domain.admin.repository.AdminRepository;
import com.example.senioron.domain.admin.repository.AdminInvitationRepository;
import com.example.senioron.domain.admin.entity.AdminInvitation;
import com.example.senioron.domain.admin.entity.AdminInvitationType;
import com.example.senioron.global.jwt.TokenHashUtil;
import java.time.LocalDateTime;
import org.springframework.http.MediaType;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.global.jwt.JwtUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class AdminSecurityIntegrationTest {

    private static final String USER_SECRET =
            "local-test-jwt-secret-key-local-test-jwt-secret-key";

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private JwtUtil jwtUtil;
    @Autowired private AdminInvitationRepository invitations;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void userTokenCannotAccessAdminApi() throws Exception {
        User user = User.builder()
                .usersId(999L)
                .loginId("security-user")
                .email("security-user@example.com")
                .password("encoded")
                .name("사용자")
                .role(Role.PARENT)
                .build();

        assertUnauthorized("/api/admin/me", jwtUtil.createAccessToken(user));
    }

    @Test
    void adminTokenCannotAccessUserApi() throws Exception {
        Admin admin = saveAdmin("cross@example.com", AdminStatus.ACTIVE);

        assertUnauthorized(
                "/api/users/me/onboarding-status",
                jwtUtil.createAdminAccessToken(admin)
        );
    }

    @Test
    void missingTokenCannotAccessAdminApi() throws Exception {
        mockMvc.perform(get("/api/admin/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userKeySignedAdminAccessTokenIsRejected() throws Exception {
        String token = Jwts.builder()
                .subject("1")
                .claim("tokenType", "admin_access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(USER_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertUnauthorized("/api/admin/me", token);
    }

    @Test
    void inactiveAdminAccessTokensAreRejected() throws Exception {
        for (AdminStatus status : new AdminStatus[]{
                AdminStatus.INVITED,
                AdminStatus.SUSPENDED
        }) {
            Admin admin = saveAdmin(status.name().toLowerCase() + "@example.com", status);
            assertUnauthorized("/api/admin/me", jwtUtil.createAdminAccessToken(admin));
        }
    }

    @Test
    void activeAdminCanAccessAdminApi() throws Exception {
        Admin admin = saveAdmin("active@example.com", AdminStatus.ACTIVE);

        mockMvc.perform(get("/api/admin/me")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtUtil.createAdminAccessToken(admin)
                        ))
                .andExpect(status().isOk());
    }

    @Test
    void moderatorCannotInviteOrResendAdmin() throws Exception {
        Admin moderator = saveAdmin("moderator@example.com", AdminStatus.ACTIVE, AdminRole.MODERATOR);
        Admin target = saveAdmin("target@example.com", AdminStatus.INVITED, AdminRole.MODERATOR);
        String bearer = "Bearer " + jwtUtil.createAdminAccessToken(moderator);
        mockMvc.perform(post("/api/admin/admins/invitations").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"new\",\"email\":\"new@example.com\",\"adminRole\":\"MODERATOR\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/admins/" + target.getAdminId() + "/invitations/resend")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isForbidden());
    }

    @Test
    void superAdminCanInviteAndReceivesAdminId() throws Exception {
        Admin superAdmin = saveAdmin("super@example.com", AdminStatus.ACTIVE, AdminRole.SUPER);
        mockMvc.perform(post("/api/admin/admins/invitations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtUtil.createAdminAccessToken(superAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"new\",\"email\":\"new-super@example.com\",\"adminRole\":\"MODERATOR\"}"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.adminId").isNumber());
    }

    @Test
    void invitationVerifyAndAcceptArePublicWithoutToken() throws Exception {
        mockMvc.perform(post("/api/admin/auth/invitations/verify").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"invalid\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/auth/invitations/accept").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"invalid\",\"password\":\"password1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void acceptedInvitationAllowsAdminLogin() throws Exception {
        Admin invited = saveAdmin("accepted@example.com", AdminStatus.INVITED, AdminRole.MODERATOR);
        String raw = "known-invitation-token";
        invitations.saveAndFlush(AdminInvitation.builder().admin(invited).type(AdminInvitationType.INVITE)
                .tokenHash(TokenHashUtil.hashHex(raw)).expiresAt(LocalDateTime.now().plusHours(1)).build());
        mockMvc.perform(post("/api/admin/auth/invitations/accept").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + raw + "\",\"password\":\"password1\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"accepted@example.com\",\"password\":\"password1\"}"))
                .andExpect(status().isOk());
    }

    private void assertUnauthorized(String path, String token) throws Exception {
        mockMvc.perform(get(path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private Admin saveAdmin(String email, AdminStatus status) {
        return saveAdmin(email, status, AdminRole.SUPER);
    }

    private Admin saveAdmin(String email, AdminStatus status, AdminRole role) {
        return adminRepository.saveAndFlush(Admin.builder()
                .email(email)
                .password(status == AdminStatus.INVITED ? null : "encoded")
                .name("관리자")
                .adminRole(role)
                .status(status)
                .build());
    }
}
