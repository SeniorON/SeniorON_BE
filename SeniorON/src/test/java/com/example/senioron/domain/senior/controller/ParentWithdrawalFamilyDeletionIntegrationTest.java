package com.example.senioron.domain.senior.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyMember;
import com.example.senioron.domain.family.repository.FamilyMemberRepository;
import com.example.senioron.domain.family.repository.FamilyRepository;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.entity.SeniorRelation;
import com.example.senioron.domain.senior.entity.SeniorWithdrawalNotice;
import com.example.senioron.domain.senior.repository.SeniorRepository;
import com.example.senioron.domain.senior.repository.SeniorWithdrawalNoticeRecipientRepository;
import com.example.senioron.domain.senior.repository.SeniorWithdrawalNoticeRepository;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.entity.UserStatus;
import com.example.senioron.domain.user.repository.UserRepository;
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
class ParentWithdrawalFamilyDeletionIntegrationTest {

    @Autowired private WebApplicationContext webContext;
    @Autowired @Qualifier("springSecurityFilterChain") private Filter securityFilter;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private UserRepository userRepository;
    @Autowired private FamilyRepository familyRepository;
    @Autowired private FamilyMemberRepository familyMemberRepository;
    @Autowired private SeniorRepository seniorRepository;
    @Autowired private SeniorWithdrawalNoticeRepository noticeRepository;
    @Autowired private SeniorWithdrawalNoticeRecipientRepository recipientRepository;
    @Autowired private EntityManager entityManager;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(webContext)
                .addFilters(securityFilter)
                .build();
    }

    @Test
    void parentWithdrawalDeletesOnlyLinkedFamilyAndKeepsNoticeApisAvailable()
            throws Exception {
        User firstChild = user("withdraw-family-first-child", Role.CHILD);
        User secondChild = user("withdraw-family-second-child", Role.CHILD);
        User targetParent = user("withdraw-family-target-parent", Role.PARENT);
        User otherParent = user("withdraw-family-other-parent", Role.PARENT);

        Family targetFamily = family("TARGET-CODE");
        Family otherFamily = family("OTHER-CODE");
        member(firstChild, targetFamily, ManagerType.PRIMARY, SeniorRelation.MOTHER);
        member(secondChild, targetFamily, ManagerType.SUB, SeniorRelation.OTHER);
        member(targetParent, targetFamily, ManagerType.NONE, null);
        member(firstChild, otherFamily, ManagerType.PRIMARY, SeniorRelation.FATHER);
        member(otherParent, otherFamily, ManagerType.NONE, null);

        Senior targetSenior = senior(
                targetFamily,
                firstChild,
                targetParent,
                "탈퇴 대상 시니어"
        );
        Senior otherSenior = senior(
                otherFamily,
                firstChild,
                otherParent,
                "유지 대상 시니어"
        );
        Long targetFamilyId = targetFamily.getFamilyId();
        Long otherFamilyId = otherFamily.getFamilyId();
        Long targetSeniorId = targetSenior.getSeniorId();
        Long otherSeniorId = otherSenior.getSeniorId();
        Long firstChildId = firstChild.getUsersId();
        Long secondChildId = secondChild.getUsersId();
        Long otherParentId = otherParent.getUsersId();
        String parentToken = bearer(targetParent);
        String firstChildToken = bearer(firstChild);
        String secondChildToken = bearer(secondChild);

        entityManager.flush();
        entityManager.clear();

        mvc.perform(delete("/api/users/me")
                        .header("Authorization", parentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmation\":\"회원 탈퇴\"}"))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();

        assertThat(seniorRepository.findById(targetSeniorId)).isEmpty();
        assertThat(familyRepository.findById(targetFamilyId)).isEmpty();
        assertThat(familyMemberRepository.findAll())
                .filteredOn(member ->
                        member.getFamily().getFamilyId().equals(targetFamilyId)
                )
                .isEmpty();
        assertThat(familyMemberRepository.findAll())
                .filteredOn(member ->
                        member.getFamily().getFamilyId().equals(otherFamilyId)
                )
                .extracting(member -> member.getUser().getUsersId())
                .containsExactlyInAnyOrder(firstChildId, otherParentId);
        assertThat(userRepository.findById(firstChildId)).hasValueSatisfying(child ->
                assertThat(child.getStatus()).isEqualTo(UserStatus.ACTIVE)
        );
        assertThat(userRepository.findById(secondChildId)).hasValueSatisfying(child ->
                assertThat(child.getStatus()).isEqualTo(UserStatus.ACTIVE)
        );
        assertThat(familyRepository.findById(otherFamilyId)).isPresent();
        assertThat(seniorRepository.findById(otherSeniorId)).isPresent();

        SeniorWithdrawalNotice notice = noticeRepository.findBySeniorId(targetSeniorId)
                .orElseThrow();
        assertThat(notice.getFamilyId()).isEqualTo(targetFamilyId);
        assertThat(recipientRepository.findAll())
                .filteredOn(recipient -> recipient.getNotice()
                        .getSeniorWithdrawalNoticeId()
                        .equals(notice.getSeniorWithdrawalNoticeId()))
                .extracting(recipient -> recipient.getRecipientUser().getUsersId())
                .containsExactlyInAnyOrder(firstChildId, secondChildId);

        mvc.perform(get("/api/seniors/withdrawal-notices")
                        .header("Authorization", firstChildToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].withdrawalNoticeId")
                        .value(notice.getSeniorWithdrawalNoticeId().intValue()))
                .andExpect(jsonPath("$.data[0].familyId").value(targetFamilyId.intValue()))
                .andExpect(jsonPath("$.data[0].seniorId").value(targetSeniorId.intValue()));

        mvc.perform(get("/api/seniors/me")
                        .header("Authorization", firstChildToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].seniorId").value(otherSeniorId.intValue()))
                .andExpect(jsonPath("$.data[0].withdrawn").value(false))
                .andExpect(jsonPath("$.data[1].seniorId").value(targetSeniorId.intValue()))
                .andExpect(jsonPath("$.data[1].withdrawn").value(true))
                .andExpect(jsonPath("$.data[1].withdrawalNoticeId")
                        .value(notice.getSeniorWithdrawalNoticeId().intValue()));

        mvc.perform(patch(
                        "/api/seniors/withdrawal-notices/{noticeId}/acknowledgement",
                        notice.getSeniorWithdrawalNoticeId()
                ).header("Authorization", firstChildToken))
                .andExpect(status().isOk());

        mvc.perform(get("/api/seniors/withdrawal-notices")
                        .header("Authorization", firstChildToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(get("/api/seniors/me")
                        .header("Authorization", firstChildToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].seniorId").value(otherSeniorId.intValue()));

        mvc.perform(get("/api/seniors/withdrawal-notices")
                        .header("Authorization", secondChildToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(get("/api/seniors/me")
                        .header("Authorization", secondChildToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].seniorId").value(targetSeniorId.intValue()))
                .andExpect(jsonPath("$.data[0].withdrawn").value(true));
    }

    private User user(String loginId, Role role) {
        return userRepository.save(User.builder()
                .loginId(loginId)
                .email(loginId + "@example.com")
                .password("encoded")
                .name(loginId)
                .role(role)
                .status(UserStatus.ACTIVE)
                .build());
    }

    private Family family(String seniorCode) {
        return familyRepository.save(
                Family.builder().seniorCode(seniorCode).build()
        );
    }

    private void member(
            User user,
            Family family,
            ManagerType managerType,
            SeniorRelation relation
    ) {
        familyMemberRepository.save(FamilyMember.builder()
                .user(user)
                .family(family)
                .managerType(managerType)
                .relation(relation)
                .customRelation(relation == SeniorRelation.OTHER ? "이모" : null)
                .build());
    }

    private Senior senior(
            Family family,
            User registeredBy,
            User parent,
            String name
    ) {
        return seniorRepository.save(Senior.builder()
                .name(name)
                .birth(LocalDate.of(1950, 1, 1))
                .phoneNumber("010-0000-0000")
                .family(family)
                .registeredBy(registeredBy)
                .parentUser(parent)
                .build());
    }

    private String bearer(User user) {
        return "Bearer " + jwtUtil.createAccessToken(user);
    }
}
