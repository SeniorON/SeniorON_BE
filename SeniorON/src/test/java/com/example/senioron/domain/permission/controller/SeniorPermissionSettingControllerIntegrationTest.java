package com.example.senioron.domain.permission.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyMember;
import com.example.senioron.domain.family.repository.FamilyMemberRepository;
import com.example.senioron.domain.family.repository.FamilyRepository;
import com.example.senioron.domain.permission.entity.SeniorPermissionSetting;
import com.example.senioron.domain.permission.repository.SeniorPermissionSettingRepository;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.repository.SeniorRepository;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.jwt.JwtUtil;
import jakarta.persistence.EntityManager;
import jakarta.servlet.Filter;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = {
        "cloud.aws.region=ap-northeast-2",
        "cloud.aws.s3.bucket=test-bucket"
})
@Transactional
class SeniorPermissionSettingControllerIntegrationTest {

    @Autowired private WebApplicationContext webContext;
    @Autowired @Qualifier("springSecurityFilterChain") private Filter securityFilter;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private UserRepository users;
    @Autowired private FamilyRepository families;
    @Autowired private FamilyMemberRepository members;
    @Autowired private SeniorRepository seniors;
    @Autowired private SeniorPermissionSettingRepository settings;
    @Autowired private EntityManager entityManager;

    private MockMvc mvc;
    private User child;
    private User parent;
    private Senior senior;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(webContext).addFilters(securityFilter).build();
        child = user("child", Role.CHILD);
        parent = user("parent", Role.PARENT);
        Family family = family("family");
        member(child, family, ManagerType.PRIMARY);
        member(parent, family, ManagerType.NONE);
        senior = senior(family, parent);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void familyMemberCanReadPermissionSetting() throws Exception {
        settings.save(SeniorPermissionSetting.builder()
                .senior(seniors.findById(senior.getSeniorId()).orElseThrow())
                .locationEnabled(false)
                .inactivityDetectionEnabled(true)
                .build());
        entityManager.flush();
        entityManager.clear();

        mvc.perform(get(path(senior)).header("Authorization", auth(child)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.seniorId").value(senior.getSeniorId().intValue()))
                .andExpect(jsonPath("$.data.locationEnabled").value(false))
                .andExpect(jsonPath("$.data.inactivityDetectionEnabled").value(true));
    }

    @Test
    void missingSettingIsCreatedWithDefaultEnabledValues() throws Exception {
        assertThat(settings.findById(senior.getSeniorId())).isEmpty();

        mvc.perform(get(path(senior)).header("Authorization", auth(child)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.locationEnabled").value(true))
                .andExpect(jsonPath("$.data.inactivityDetectionEnabled").value(true));

        entityManager.flush();
        entityManager.clear();
        SeniorPermissionSetting saved = settings.findById(senior.getSeniorId()).orElseThrow();
        assertThat(saved.getLocationEnabled()).isTrue();
        assertThat(saved.getInactivityDetectionEnabled()).isTrue();
    }

    @Test
    void parentFamilyMemberCanReadPermissionSetting() throws Exception {
        mvc.perform(get(path(senior)).header("Authorization", auth(parent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.locationEnabled").value(true))
                .andExpect(jsonPath("$.data.inactivityDetectionEnabled").value(true));
    }

    @Test
    void unrelatedUserCannotReadPermissionSetting() throws Exception {
        User outsider = user("outsider", Role.CHILD);
        Family otherFamily = family("other");
        member(outsider, otherFamily, ManagerType.PRIMARY);
        entityManager.flush();
        entityManager.clear();

        mvc.perform(get(path(senior)).header("Authorization", auth(outsider)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));
    }

    @Test
    void missingSeniorReturnsNotFound() throws Exception {
        mvc.perform(get("/api/seniors/{seniorId}/permission-settings", Long.MAX_VALUE)
                        .header("Authorization", auth(child)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.SENIOR_NOT_FOUND.getCode()));
    }

    @Test
    void parentCanTurnLocationOff() throws Exception {
        mvc.perform(patch(myPath())
                        .header("Authorization", auth(parent))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locationEnabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.seniorId").value(senior.getSeniorId().intValue()))
                .andExpect(jsonPath("$.data.locationEnabled").value(false))
                .andExpect(jsonPath("$.data.inactivityDetectionEnabled").value(true));
    }

    @Test
    void parentCanTurnLocationOn() throws Exception {
        saveSetting(false, true);

        mvc.perform(patch(myPath())
                        .header("Authorization", auth(parent))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locationEnabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.locationEnabled").value(true))
                .andExpect(jsonPath("$.data.inactivityDetectionEnabled").value(true));
    }

    @Test
    void parentCanTurnInactivityDetectionOff() throws Exception {
        mvc.perform(patch(myPath())
                        .header("Authorization", auth(parent))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inactivityDetectionEnabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.locationEnabled").value(true))
                .andExpect(jsonPath("$.data.inactivityDetectionEnabled").value(false));
    }

    @Test
    void parentCanTurnInactivityDetectionOn() throws Exception {
        saveSetting(true, false);

        mvc.perform(patch(myPath())
                        .header("Authorization", auth(parent))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inactivityDetectionEnabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.locationEnabled").value(true))
                .andExpect(jsonPath("$.data.inactivityDetectionEnabled").value(true));
    }

    @Test
    void partialPatchKeepsUnspecifiedSettingValue() throws Exception {
        saveSetting(true, false);

        mvc.perform(patch(myPath())
                        .header("Authorization", auth(parent))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locationEnabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.locationEnabled").value(false))
                .andExpect(jsonPath("$.data.inactivityDetectionEnabled").value(false));

        entityManager.flush();
        entityManager.clear();
        SeniorPermissionSetting saved = settings.findById(senior.getSeniorId()).orElseThrow();
        assertThat(saved.getLocationEnabled()).isFalse();
        assertThat(saved.getInactivityDetectionEnabled()).isFalse();
    }

    @Test
    void childCannotUpdatePermissionSetting() throws Exception {
        mvc.perform(patch(myPath())
                        .header("Authorization", auth(child))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locationEnabled\":false}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(
                        ErrorCode.SENIOR_PERMISSION_SETTING_PARENT_ONLY.getCode()));
    }

    @Test
    void parentUpdatesOnlySeniorLinkedToLoginUser() throws Exception {
        User otherParent = user("other-parent", Role.PARENT);
        Family otherFamily = family("other-family");
        member(otherParent, otherFamily, ManagerType.NONE);
        Senior otherSenior = seniors.save(Senior.builder()
                .name("다른 시니어")
                .birth(LocalDate.of(1955, 1, 1))
                .phoneNumber("01087654321")
                .family(otherFamily)
                .registeredBy(child)
                .parentUser(otherParent)
                .build());
        saveSetting(true, true);
        settings.save(SeniorPermissionSetting.builder()
                .senior(otherSenior)
                .locationEnabled(true)
                .inactivityDetectionEnabled(true)
                .build());
        entityManager.flush();
        entityManager.clear();

        mvc.perform(patch(myPath())
                        .header("Authorization", auth(parent))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locationEnabled\":false,\"inactivityDetectionEnabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.seniorId").value(senior.getSeniorId().intValue()))
                .andExpect(jsonPath("$.data.locationEnabled").value(false))
                .andExpect(jsonPath("$.data.inactivityDetectionEnabled").value(false));

        entityManager.flush();
        entityManager.clear();
        SeniorPermissionSetting linkedSetting = settings.findById(senior.getSeniorId()).orElseThrow();
        SeniorPermissionSetting otherSetting = settings.findById(otherSenior.getSeniorId()).orElseThrow();
        assertThat(linkedSetting.getLocationEnabled()).isFalse();
        assertThat(linkedSetting.getInactivityDetectionEnabled()).isFalse();
        assertThat(otherSetting.getLocationEnabled()).isTrue();
        assertThat(otherSetting.getInactivityDetectionEnabled()).isTrue();
    }

    @Test
    void parentWithoutLinkedSeniorCannotUpdatePermissionSetting() throws Exception {
        User unlinkedParent = user("unlinked-parent", Role.PARENT);

        mvc.perform(patch(myPath())
                        .header("Authorization", auth(unlinkedParent))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locationEnabled\":false}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.SENIOR_NOT_FOUND.getCode()));
    }

    @Test
    void emptyPatchRequestIsRejected() throws Exception {
        mvc.perform(patch(myPath())
                        .header("Authorization", auth(parent))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(
                        ErrorCode.SENIOR_PERMISSION_SETTING_UPDATE_EMPTY.getCode()));
    }

    private User user(String prefix, Role role) {
        return users.save(User.builder()
                .loginId(prefix + "-" + System.nanoTime())
                .name(prefix)
                .role(role)
                .build());
    }

    private Family family(String prefix) {
        return families.save(Family.builder()
                .seniorCode(prefix + "-" + System.nanoTime())
                .build());
    }

    private void member(User user, Family family, ManagerType managerType) {
        members.save(FamilyMember.builder()
                .user(user)
                .family(family)
                .managerType(managerType)
                .build());
    }

    private Senior senior(Family family, User parent) {
        return seniors.save(Senior.builder()
                .name("시니어")
                .birth(LocalDate.of(1950, 1, 1))
                .phoneNumber("01012345678")
                .family(family)
                .registeredBy(child)
                .parentUser(parent)
                .build());
    }

    private String auth(User user) {
        return "Bearer " + jwtUtil.createAccessToken(user);
    }

    private String path(Senior senior) {
        return "/api/seniors/" + senior.getSeniorId() + "/permission-settings";
    }

    private String myPath() {
        return "/api/seniors/me/permission-settings";
    }

    private void saveSetting(boolean locationEnabled, boolean inactivityDetectionEnabled) {
        settings.save(SeniorPermissionSetting.builder()
                .senior(seniors.findById(senior.getSeniorId()).orElseThrow())
                .locationEnabled(locationEnabled)
                .inactivityDetectionEnabled(inactivityDetectionEnabled)
                .build());
        entityManager.flush();
        entityManager.clear();
    }
}
