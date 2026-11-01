package com.example.senioron.domain.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.example.senioron.domain.admin.entity.Admin;
import com.example.senioron.domain.admin.entity.AdminActivityLog;
import com.example.senioron.domain.admin.entity.AdminActivityTargetType;
import com.example.senioron.domain.admin.entity.AdminActivityType;
import com.example.senioron.domain.admin.entity.AdminRefreshToken;
import com.example.senioron.domain.admin.entity.AdminRole;
import com.example.senioron.domain.admin.entity.AdminStatus;
import com.example.senioron.domain.admin.repository.AdminActivityLogRepository;
import com.example.senioron.domain.admin.repository.AdminRefreshTokenRepository;
import com.example.senioron.domain.admin.repository.AdminRepository;
import com.example.senioron.global.jwt.JwtUtil;
import com.example.senioron.global.jwt.TokenHashUtil;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class AdminManagementIntegrationTest {

    private static final String CLIENT_IP = "203.0.113.10";

    private MockMvc mockMvc;

    @Autowired private WebApplicationContext context;
    @Autowired private AdminRepository admins;
    @Autowired private AdminRefreshTokenRepository refreshTokens;
    @Autowired private AdminActivityLogRepository activityLogs;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private EntityManager entityManager;

    private Admin superAdmin;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        superAdmin = save("actor", AdminRole.SUPER, AdminStatus.ACTIVE);
    }

    @Test
    void searchMatchesNameOrEmailIgnoringCase() throws Exception {
        String keyword = uniqueKeyword();
        Admin byName = save("Name-" + keyword.toUpperCase(), AdminRole.MODERATOR, AdminStatus.ACTIVE);
        Admin byEmail = admins.saveAndFlush(Admin.builder()
                .email(keyword.toUpperCase() + "@Example.com")
                .password("encoded")
                .name("이메일 매칭")
                .adminRole(AdminRole.MODERATOR)
                .status(AdminStatus.INVITED)
                .build());
        save("unrelated", AdminRole.MODERATOR, AdminStatus.ACTIVE);

        search(superAdmin, "?keyword=" + keyword)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON_200"))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].adminId").value(byEmail.getAdminId()))
                .andExpect(jsonPath("$.data.content[0].email").value(byEmail.getEmail()))
                .andExpect(jsonPath("$.data.content[0].status").value("INVITED"))
                .andExpect(jsonPath("$.data.content[1].adminId").value(byName.getAdminId()))
                .andExpect(jsonPath("$.data.content[1].name").value(byName.getName()))
                .andExpect(jsonPath("$.data.content[1].adminRole").value("MODERATOR"))
                .andExpect(jsonPath("$.data.content[1].lastLoginAt").hasJsonPath());
    }

    @Test
    void searchTreatsLikeWildcardsAsLiterals() throws Exception {
        String keyword = uniqueKeyword();
        save(keyword + "-plain", AdminRole.MODERATOR, AdminStatus.ACTIVE);
        Admin underscore = save(keyword + "_under", AdminRole.MODERATOR, AdminStatus.ACTIVE);

        search(superAdmin, "?keyword=" + keyword + "_")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].adminId").value(underscore.getAdminId()));
    }

    @Test
    void searchSortsByCreatedAtDescThenAdminIdDescAndPaginates() throws Exception {
        String keyword = uniqueKeyword();
        Admin oldestId = save(keyword + "-1", AdminRole.MODERATOR, AdminStatus.ACTIVE);
        Admin middleId = save(keyword + "-2", AdminRole.MODERATOR, AdminStatus.ACTIVE);
        Admin newestId = save(keyword + "-3", AdminRole.MODERATOR, AdminStatus.ACTIVE);
        LocalDateTime base = LocalDateTime.of(2030, 1, 1, 0, 0);
        setCreatedAt(oldestId, base.plusDays(1));
        setCreatedAt(middleId, base);
        setCreatedAt(newestId, base);

        search(superAdmin, "?keyword=" + keyword + "&page=0&size=2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].adminId").value(oldestId.getAdminId()))
                .andExpect(jsonPath("$.data.content[1].adminId").value(newestId.getAdminId()));

        search(superAdmin, "?keyword=" + keyword + "&page=1&size=2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].adminId").value(middleId.getAdminId()));
    }

    @Test
    void searchWithoutKeywordReturnsAllAndCapsSizeAt100() throws Exception {
        long total = admins.count();

        search(superAdmin, "?size=1000")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(100))
                .andExpect(jsonPath("$.data.totalElements").value(total));

        search(superAdmin, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20));
    }

    @Test
    void searchRejectsNegativePageAndNonPositiveSize() throws Exception {
        search(superAdmin, "?page=-1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"));
        search(superAdmin, "?size=0")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"));
    }

    @Test
    void moderatorCanSearchAdmins() throws Exception {
        Admin moderator = save("list-moderator", AdminRole.MODERATOR, AdminStatus.ACTIVE);

        search(moderator, "")
                .andExpect(status().isOk());
    }

    @Test
    void searchWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/admins"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void moderatorCannotUpdateAdmin() throws Exception {
        Admin moderator = save("update-moderator", AdminRole.MODERATOR, AdminStatus.ACTIVE);
        Admin target = save("update-target", AdminRole.MODERATOR, AdminStatus.ACTIVE);

        update(moderator, target.getAdminId(), "{\"active\":false}")
                .andExpect(status().isForbidden());
        assertThat(target.getStatus()).isEqualTo(AdminStatus.ACTIVE);
    }

    @Test
    void updatingSelfIsRejected() throws Exception {
        update(superAdmin, superAdmin.getAdminId(), "{\"adminRole\":\"MODERATOR\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ADMIN400_2"))
                .andExpect(jsonPath("$.message").value("본인 계정은 변경할 수 없습니다."));
    }

    @Test
    void updatingMissingAdminReturnsNotFound() throws Exception {
        update(superAdmin, Long.MAX_VALUE, "{\"active\":false}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ADMIN404"));
    }

    @Test
    void updateWithoutAnyFieldIsBadRequest() throws Exception {
        Admin target = save("empty-target", AdminRole.MODERATOR, AdminStatus.ACTIVE);

        update(superAdmin, target.getAdminId(), "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"));
    }

    @Test
    void roleChangeIsAppliedAndRecorded() throws Exception {
        Admin target = save("role-target", AdminRole.MODERATOR, AdminStatus.INVITED);

        update(superAdmin, target.getAdminId(), "{\"adminRole\":\"SUPER\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON_200"))
                .andExpect(jsonPath("$.data.adminId").value(target.getAdminId()))
                .andExpect(jsonPath("$.data.name").value(target.getName()))
                .andExpect(jsonPath("$.data.email").value(target.getEmail()))
                .andExpect(jsonPath("$.data.adminRole").value("SUPER"))
                .andExpect(jsonPath("$.data.status").value("INVITED"));

        assertThat(reload(target).getAdminRole()).isEqualTo(AdminRole.SUPER);
        assertThat(logsFor(target)).singleElement().satisfies(log -> {
            assertThat(log.getActivityType()).isEqualTo(AdminActivityType.ADMIN_ROLE_CHANGED);
            assertThat(log.getTargetType()).isEqualTo(AdminActivityTargetType.ADMIN);
            assertThat(log.getTargetName()).isEqualTo(target.getName());
            assertThat(log.getAdmin().getAdminId()).isEqualTo(superAdmin.getAdminId());
            assertThat(log.getIpAddress()).isEqualTo(CLIENT_IP);
        });
    }

    @Test
    void superCanDemoteAnotherSuperWhileActiveSuperRemains() throws Exception {
        Admin target = save("demote-target", AdminRole.SUPER, AdminStatus.ACTIVE);

        update(superAdmin, target.getAdminId(), "{\"adminRole\":\"MODERATOR\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.adminRole").value("MODERATOR"));
    }

    @Test
    void suspendRevokesRefreshTokensAndBlocksAccessToken() throws Exception {
        Admin target = save("suspend-target", AdminRole.SUPER, AdminStatus.ACTIVE);
        String accessToken = jwtUtil.createAdminAccessToken(target);
        String refreshToken = jwtUtil.createAdminRefreshToken(target);
        AdminRefreshToken savedToken = refreshTokens.saveAndFlush(AdminRefreshToken.builder()
                .admin(target)
                .tokenHash(TokenHashUtil.hash(refreshToken))
                .expiresAt(jwtUtil.getAdminRefreshTokenExpiresAt())
                .build());
        mockMvc.perform(get("/api/admin/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
        entityManager.clear();

        update(superAdmin, target.getAdminId(), "{\"active\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"));

        assertThat(reload(target).getStatus()).isEqualTo(AdminStatus.SUSPENDED);
        assertThat(refreshTokens.findById(savedToken.getId()).orElseThrow().isRevoked()).isTrue();
        mockMvc.perform(post("/api/admin/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ADMIN401_1"));
        mockMvc.perform(get("/api/admin/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
        assertThat(logsFor(target)).singleElement()
                .extracting(AdminActivityLog::getActivityType)
                .isEqualTo(AdminActivityType.ADMIN_SUSPENDED);
    }

    @Test
    void invitedAdminCanBeSuspended() throws Exception {
        Admin target = save("suspend-invited", AdminRole.MODERATOR, AdminStatus.INVITED);

        update(superAdmin, target.getAdminId(), "{\"active\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"));
    }

    @Test
    void reactivationRestoresActiveWhenPasswordExists() throws Exception {
        Admin target = save("reactivate-active", AdminRole.MODERATOR, AdminStatus.SUSPENDED);

        update(superAdmin, target.getAdminId(), "{\"active\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        assertThat(logsFor(target)).singleElement()
                .extracting(AdminActivityLog::getActivityType)
                .isEqualTo(AdminActivityType.ADMIN_ACTIVATED);
    }

    @Test
    void reactivationRestoresInvitedWhenPasswordIsMissing() throws Exception {
        Admin target = admins.saveAndFlush(Admin.builder()
                .email(uniqueKeyword() + "@example.com")
                .name("초대 후 정지")
                .adminRole(AdminRole.MODERATOR)
                .status(AdminStatus.SUSPENDED)
                .build());

        update(superAdmin, target.getAdminId(), "{\"active\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INVITED"));

        assertThat(reload(target).getStatus()).isEqualTo(AdminStatus.INVITED);
    }

    @Test
    void roleChangeAndSuspensionTogetherRecordBothActivities() throws Exception {
        Admin target = save("combined-target", AdminRole.MODERATOR, AdminStatus.ACTIVE);

        update(superAdmin, target.getAdminId(), "{\"adminRole\":\"SUPER\",\"active\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.adminRole").value("SUPER"))
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"));

        assertThat(logsFor(target))
                .extracting(AdminActivityLog::getActivityType)
                .containsExactlyInAnyOrder(
                        AdminActivityType.ADMIN_ROLE_CHANGED,
                        AdminActivityType.ADMIN_SUSPENDED
                );
    }

    @Test
    void unchangedUpdateDoesNotRecordActivity() throws Exception {
        Admin active = save("unchanged-active", AdminRole.MODERATOR, AdminStatus.ACTIVE);
        Admin suspended = save("unchanged-suspended", AdminRole.SUPER, AdminStatus.SUSPENDED);

        update(superAdmin, active.getAdminId(), "{\"adminRole\":\"MODERATOR\",\"active\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        update(superAdmin, suspended.getAdminId(), "{\"active\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"));

        assertThat(logsFor(active)).isEmpty();
        assertThat(logsFor(suspended)).isEmpty();
    }

    private ResultActions search(Admin actor, String query) throws Exception {
        return mockMvc.perform(get("/api/admin/admins" + query)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtUtil.createAdminAccessToken(actor)));
    }

    private ResultActions update(Admin actor, Long adminId, String body) throws Exception {
        return mockMvc.perform(patch("/api/admin/admins/" + adminId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtUtil.createAdminAccessToken(actor))
                .header("X-Real-IP", CLIENT_IP)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private Admin save(String name, AdminRole role, AdminStatus status) {
        return admins.saveAndFlush(Admin.builder()
                .email(uniqueKeyword() + "@example.com")
                .password(status == AdminStatus.INVITED ? null : "encoded")
                .name(name)
                .adminRole(role)
                .status(status)
                .build());
    }

    private Admin reload(Admin admin) {
        entityManager.flush();
        entityManager.clear();
        return admins.findById(admin.getAdminId()).orElseThrow();
    }

    private List<AdminActivityLog> logsFor(Admin target) {
        return activityLogs.findAll().stream()
                .filter(log -> log.getTargetId().equals(target.getAdminId()))
                .toList();
    }

    private void setCreatedAt(Admin admin, LocalDateTime createdAt) {
        entityManager.createNativeQuery("update admin set created_at = :createdAt where admin_id = :adminId")
                .setParameter("createdAt", createdAt)
                .setParameter("adminId", admin.getAdminId())
                .executeUpdate();
    }

    private String uniqueKeyword() {
        return "kw" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
