package com.example.senioron.domain.inactivity.controller;

import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyMember;
import com.example.senioron.domain.family.repository.FamilyMemberRepository;
import com.example.senioron.domain.family.repository.FamilyRepository;
import com.example.senioron.domain.inactivity.entity.InactivitySetting;
import com.example.senioron.domain.inactivity.repository.InactivitySettingRepository;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "cloud.aws.region=ap-northeast-2",
        "cloud.aws.s3.bucket=test-bucket"
})
@Transactional
class InactivitySettingControllerIntegrationTest {
    @Autowired private WebApplicationContext webContext;
    @Autowired @Qualifier("springSecurityFilterChain") private Filter securityFilter;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private UserRepository users;
    @Autowired private FamilyRepository families;
    @Autowired private FamilyMemberRepository members;
    @Autowired private SeniorRepository seniors;
    @Autowired private InactivitySettingRepository settings;
    @Autowired private EntityManager entityManager;

    private MockMvc mvc;
    private User child;
    private User parentA;
    private User parentB;
    private Senior seniorA;
    private Senior seniorB;
    private String childAuth;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(webContext).addFilters(securityFilter).build();
        child = user("child", Role.CHILD);
        parentA = user("parent-a", Role.PARENT);
        parentB = user("parent-b", Role.PARENT);
        Family familyA = family("A");
        Family familyB = family("B");
        member(child, familyA, ManagerType.PRIMARY);
        member(parentA, familyA, ManagerType.NONE);
        member(child, familyB, ManagerType.SUB);
        member(parentB, familyB, ManagerType.NONE);
        seniorA = senior(familyA, parentA);
        seniorB = senior(familyB, parentB);
        settings.save(InactivitySetting.builder().user(parentA).thresholdHours(6).build());
        settings.save(InactivitySetting.builder().user(parentB).thresholdHours(10).isEnabled(false).build());
        entityManager.flush();
        // 실제 DB 멤버십으로 검증하며 User의 transient Family fallback에 의존하지 않는다.
        entityManager.clear();
        childAuth = auth(child);
    }

    @Test
    void childCanReadAndUpdateSecondFamilyWithoutChangingFirstParent() throws Exception {
        User persistedChild = users.findById(child.getUsersId()).orElseThrow();
        assertThat(ReflectionTestUtils.getField(persistedChild, "family")).isNull();
        assertThat(persistedChild.getFamily().getFamilyId()).isEqualTo(seniorA.getFamily().getFamilyId());
        assertThat(parentB.getUsersId()).isNotEqualTo(seniorB.getSeniorId());
        mvc.perform(get(path(seniorB)).header("Authorization", childAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usersId").value(parentB.getUsersId().intValue()))
                .andExpect(jsonPath("$.data.thresholdHours").value(10));
        mvc.perform(patch(path(seniorB)).header("Authorization", childAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"thresholdHours\":9}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usersId").value(parentB.getUsersId().intValue()))
                .andExpect(jsonPath("$.data.thresholdHours").value(9))
                .andExpect(jsonPath("$.data.isEnabled").value(false));
        entityManager.flush();
        entityManager.clear();
        assertThat(settings.findById(parentA.getUsersId()).orElseThrow().getThresholdHours()).isEqualTo(6);
        assertThat(settings.findById(parentB.getUsersId()).orElseThrow().getThresholdHours()).isEqualTo(9);
        mvc.perform(get(path(seniorA)).header("Authorization", childAuth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.thresholdHours").value(6));
        mvc.perform(patch(path(seniorA)).header("Authorization", childAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"thresholdHours\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.thresholdHours").value(1));
        entityManager.flush();
        entityManager.clear();
        assertThat(settings.findById(parentA.getUsersId()).orElseThrow().getThresholdHours()).isEqualTo(1);
        assertThat(settings.findById(parentB.getUsersId()).orElseThrow().getThresholdHours()).isEqualTo(9);
    }

    @Test
    void unrelatedChildCannotReadOrUpdateSettings() throws Exception {
        User outsider = user("outsider", Role.CHILD);
        member(outsider, families.findById(seniorA.getFamily().getFamilyId()).orElseThrow(), ManagerType.PRIMARY);
        entityManager.flush();
        String outsiderAuth = auth(outsider);
        mvc.perform(get(path(seniorB)).header("Authorization", outsiderAuth))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));
        mvc.perform(patch(path(seniorB)).header("Authorization", outsiderAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"thresholdHours\":9}"))
                .andExpect(status().isForbidden());
        assertThat(settings.findById(parentB.getUsersId()).orElseThrow().getThresholdHours()).isEqualTo(10);
    }

    @Test
    void removedMemberCannotAccessEvenWithStaleLegacyFamily() throws Exception {
        mvc.perform(get(path(seniorB)).header("Authorization", childAuth)).andExpect(status().isOk());
        User persistedChild = users.findById(child.getUsersId()).orElseThrow();
        Family targetFamily = families.findById(seniorB.getFamily().getFamilyId()).orElseThrow();
        members.deleteByUserAndFamily(persistedChild, targetFamily);
        entityManager.flush();
        entityManager.clear();
        // 탈퇴 후에도 레거시 캐시에 같은 가족이 남아 있어도 실제 멤버십을 우선한다.
        users.findById(child.getUsersId()).orElseThrow().updateFamily(targetFamily);
        mvc.perform(get(path(seniorB)).header("Authorization", childAuth))
                .andExpect(status().isForbidden());
        mvc.perform(patch(path(seniorB)).header("Authorization", childAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"thresholdHours\":9}"))
                .andExpect(status().isForbidden());
        assertThat(settings.findById(parentB.getUsersId()).orElseThrow().getThresholdHours()).isEqualTo(10);
    }

    @Test
    void noneMemberKeepsExistingAccessAndParentLegacyFamilyIsNotRequired() throws Exception {
        User anotherChild = user("none-child", Role.CHILD);
        Family targetFamily = families.findById(seniorB.getFamily().getFamilyId()).orElseThrow();
        member(anotherChild, targetFamily, ManagerType.NONE);
        members.deleteByUserAndFamily(users.findById(parentB.getUsersId()).orElseThrow(), targetFamily);
        entityManager.flush();
        entityManager.clear();
        assertThat(users.findById(parentB.getUsersId()).orElseThrow().getFamily()).isNull();
        String auth = auth(anotherChild);
        mvc.perform(get(path(seniorB)).header("Authorization", auth)).andExpect(status().isOk());
        mvc.perform(patch(path(seniorB)).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"thresholdHours\":24}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.thresholdHours").value(24));
    }

    @Test
    void childTargetCannotBeReadOrUpdated() throws Exception {
        String targetPath = "/api/inactivity-settings/" + child.getUsersId();
        mvc.perform(get(targetPath).header("Authorization", childAuth))
                .andExpect(status().isForbidden());
        mvc.perform(patch(targetPath).header("Authorization", childAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"thresholdHours\":8}"))
                .andExpect(status().isForbidden());
        assertThat(settings.findById(child.getUsersId())).isEmpty();
    }

    @Test
    void missingTargetUserReturnsNotFound() throws Exception {
        mvc.perform(get("/api/inactivity-settings/{targetUserId}", Long.MAX_VALUE)
                        .header("Authorization", childAuth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.USER_NOT_FOUND.getCode()));
    }

    @Test
    void parentWithoutLinkedSeniorReturnsExplicitError() throws Exception {
        Family unlinkedFamily = family("unlinked");
        member(child, unlinkedFamily, ManagerType.SUB);
        User unlinkedParent = user("unlinked-parent", Role.PARENT);
        member(unlinkedParent, unlinkedFamily, ManagerType.NONE);
        senior(unlinkedFamily, null);
        entityManager.flush();
        String targetPath = "/api/inactivity-settings/" + unlinkedParent.getUsersId();
        mvc.perform(get(targetPath).header("Authorization", childAuth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.SENIOR_NOT_FOUND.getCode()));
        mvc.perform(patch(targetPath).header("Authorization", childAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"thresholdHours\":8}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.SENIOR_NOT_FOUND.getCode()));
        assertThat(settings.findById(unlinkedParent.getUsersId())).isEmpty();
    }

    @Test
    void parentSelfLookupKeepsUserBasedSettingsAndChildRouteIsDenied() throws Exception {
        String parentAuth = auth(parentB);
        mvc.perform(get("/api/inactivity-settings/me").header("Authorization", parentAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usersId").value(parentB.getUsersId().intValue()))
                .andExpect(jsonPath("$.data.thresholdHours").value(10));
        mvc.perform(get(path(seniorB)).header("Authorization", parentAuth))
                .andExpect(status().isForbidden());
        mvc.perform(patch(path(seniorB)).header("Authorization", parentAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"thresholdHours\":8}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void lookupPreservesExistingFourHourLazyDefault() throws Exception {
        settings.deleteById(parentB.getUsersId());
        entityManager.flush();
        entityManager.clear();
        mvc.perform(get(path(seniorB)).header("Authorization", childAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.thresholdHours").value(4))
                .andExpect(jsonPath("$.data.isEnabled").value(true));
        entityManager.flush();
        entityManager.clear();
        assertThat(settings.findById(parentB.getUsersId()).orElseThrow().getThresholdHours()).isEqualTo(4);
        assertThat(settings.findById(parentA.getUsersId()).orElseThrow().getThresholdHours()).isEqualTo(6);
    }

    @Test
    void parentSelfLookupPreservesFourHourLazyDefault() throws Exception {
        settings.deleteById(parentB.getUsersId());
        entityManager.flush();
        entityManager.clear();
        mvc.perform(get("/api/inactivity-settings/me").header("Authorization", auth(parentB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usersId").value(parentB.getUsersId().intValue()))
                .andExpect(jsonPath("$.data.thresholdHours").value(4));
    }

    @Test
    void updateCreatesMissingParentSettingWithoutTouchingOtherParent() throws Exception {
        settings.deleteById(parentB.getUsersId());
        entityManager.flush();
        entityManager.clear();
        mvc.perform(patch(path(seniorB)).header("Authorization", childAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"thresholdHours\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usersId").value(parentB.getUsersId().intValue()))
                .andExpect(jsonPath("$.data.thresholdHours").value(7))
                .andExpect(jsonPath("$.data.isEnabled").value(true));
        entityManager.flush();
        entityManager.clear();
        assertThat(settings.findById(parentB.getUsersId()).orElseThrow().getThresholdHours()).isEqualTo(7);
        assertThat(settings.findById(parentA.getUsersId()).orElseThrow().getThresholdHours()).isEqualTo(6);
    }

    @Test
    void invalidHoursReturnBadRequestAndKeepExistingSetting() throws Exception {
        for (String body : new String[]{"{\"thresholdHours\":0}", "{\"thresholdHours\":25}",
                "{\"thresholdHours\":null}", "{}"}) {
            mvc.perform(patch(path(seniorB)).header("Authorization", childAuth)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(settings.findById(parentB.getUsersId()).orElseThrow().getThresholdHours()).isEqualTo(10);
    }

    private User user(String prefix, Role role) {
        return users.save(User.builder().loginId(prefix + "-" + System.nanoTime()).name(prefix).role(role).build());
    }

    private Family family(String prefix) {
        return families.save(Family.builder().seniorCode(prefix + "-" + System.nanoTime()).build());
    }

    private void member(User user, Family family, ManagerType managerType) {
        members.save(FamilyMember.builder().user(user).family(family).managerType(managerType).build());
    }

    private Senior senior(Family family, User parent) {
        return seniors.save(Senior.builder().name("시니어").birth(LocalDate.of(1950, 1, 1))
                .phoneNumber("01012345678").family(family).registeredBy(child).parentUser(parent).build());
    }

    private String auth(User user) {
        return "Bearer " + jwtUtil.createAccessToken(user);
    }

    private String path(Senior senior) {
        return "/api/inactivity-settings/" + senior.getParentUser().getUsersId();
    }
}
