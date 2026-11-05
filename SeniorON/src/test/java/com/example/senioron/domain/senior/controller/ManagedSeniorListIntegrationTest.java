package com.example.senioron.domain.senior.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.senioron.domain.family.dto.request.FamilyJoinRequest;
import com.example.senioron.domain.family.dto.response.SeniorCodeCreateResponse;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyMember;
import com.example.senioron.domain.family.repository.FamilyMemberRepository;
import com.example.senioron.domain.family.repository.FamilyRepository;
import com.example.senioron.domain.family.service.FamilyService;
import com.example.senioron.domain.senior.dto.request.SeniorCreateRequest;
import com.example.senioron.domain.senior.dto.response.SeniorCreateResponse;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.entity.SeniorRelation;
import com.example.senioron.domain.senior.entity.SeniorWithdrawalNotice;
import com.example.senioron.domain.senior.entity.SeniorWithdrawalNoticeRecipient;
import com.example.senioron.domain.senior.repository.SeniorRepository;
import com.example.senioron.domain.senior.repository.SeniorWithdrawalNoticeRecipientRepository;
import com.example.senioron.domain.senior.repository.SeniorWithdrawalNoticeRepository;
import com.example.senioron.domain.senior.service.SeniorService;
import com.example.senioron.domain.senior.service.SeniorWithdrawalNoticeService;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.entity.UserStatus;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.jwt.JwtUtil;
import jakarta.persistence.EntityManager;
import jakarta.servlet.Filter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = {
        "cloud.aws.region=ap-northeast-2",
        "cloud.aws.s3.bucket=test-bucket"
})
@Transactional
class ManagedSeniorListIntegrationTest {

    private static final String PATH = "/api/seniors/me";

    @Autowired private WebApplicationContext webContext;
    @Autowired @Qualifier("springSecurityFilterChain") private Filter securityFilter;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private UserRepository userRepository;
    @Autowired private FamilyRepository familyRepository;
    @Autowired private FamilyMemberRepository familyMemberRepository;
    @Autowired private SeniorRepository seniorRepository;
    @Autowired private SeniorWithdrawalNoticeRepository noticeRepository;
    @Autowired private SeniorWithdrawalNoticeRecipientRepository recipientRepository;
    @Autowired private SeniorWithdrawalNoticeService noticeService;
    @Autowired private SeniorService seniorService;
    @Autowired private FamilyService familyService;
    @Autowired private EntityManager entityManager;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(webContext)
                .addFilters(securityFilter)
                .build();
    }

    @Test
    void returnsNormalAndCurrentChildUnacknowledgedWithdrawalCards() throws Exception {
        User child = user("managed-list-child", Role.CHILD);
        User otherChild = user("managed-list-other-child", Role.CHILD);
        User unrelatedChild = user("managed-list-unrelated-child", Role.CHILD);
        User parent = user("managed-list-parent", Role.PARENT);

        Family normalFamily = family("NORMAL01");
        member(child, normalFamily, ManagerType.PRIMARY, SeniorRelation.FATHER);
        Senior normalSenior = senior(normalFamily, child, parent, "정상 시니어");

        Family withdrawnFamily = family(null);
        member(child, withdrawnFamily, ManagerType.PRIMARY, SeniorRelation.MOTHER);
        member(otherChild, withdrawnFamily, ManagerType.SUB, SeniorRelation.OTHER);
        SeniorWithdrawalNotice withdrawalNotice = notice(
                7001L,
                withdrawnFamily.getFamilyId(),
                "탈퇴 시니어",
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusDays(30)
        );
        recipient(withdrawalNotice, child, SeniorRelation.MOTHER, null, null);
        recipient(withdrawalNotice, otherChild, SeniorRelation.OTHER, "이모", null);

        SeniorWithdrawalNotice unrelatedNotice = notice(
                7002L,
                9002L,
                "다른 자녀 시니어",
                LocalDateTime.now().minusMinutes(30),
                LocalDateTime.now().plusDays(30)
        );
        recipient(unrelatedNotice, unrelatedChild, SeniorRelation.MOTHER, null, null);

        Family expiredFamily = family(null);
        member(child, expiredFamily, ManagerType.SUB, SeniorRelation.GRANDPARENT);
        SeniorWithdrawalNotice expiredNotice = notice(
                7003L,
                expiredFamily.getFamilyId(),
                "만료 시니어",
                LocalDateTime.now().minusDays(31),
                LocalDateTime.now().minusSeconds(1)
        );
        recipient(expiredNotice, child, SeniorRelation.GRANDPARENT, null, null);

        entityManager.flush();
        entityManager.clear();

        mvc.perform(get(PATH).header("Authorization", bearer(child)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].familyId")
                        .value(normalFamily.getFamilyId().intValue()))
                .andExpect(jsonPath("$.data[0].seniorId")
                        .value(normalSenior.getSeniorId().intValue()))
                .andExpect(jsonPath("$.data[0].seniorName").value("정상 시니어"))
                .andExpect(jsonPath("$.data[0].relation").value("FATHER"))
                .andExpect(jsonPath("$.data[0].seniorProfileCompleted").value(true))
                .andExpect(jsonPath("$.data[0].withdrawn").value(false))
                .andExpect(jsonPath("$.data[0].withdrawalNoticeId").value(nullValue()))
                .andExpect(jsonPath("$.data[1].familyId")
                        .value(withdrawnFamily.getFamilyId().intValue()))
                .andExpect(jsonPath("$.data[1].seniorId").value(7001))
                .andExpect(jsonPath("$.data[1].seniorName").value("탈퇴 시니어"))
                .andExpect(jsonPath("$.data[1].relation").value("MOTHER"))
                .andExpect(jsonPath("$.data[1].seniorProfileCompleted").value(true))
                .andExpect(jsonPath("$.data[1].withdrawn").value(true))
                .andExpect(jsonPath("$.data[1].withdrawalNoticeId")
                        .value(withdrawalNotice.getSeniorWithdrawalNoticeId().intValue()));

        noticeService.acknowledgeNotice(child, withdrawalNotice.getSeniorWithdrawalNoticeId());
        entityManager.flush();
        entityManager.clear();

        mvc.perform(get(PATH).header("Authorization", bearer(child)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].seniorId")
                        .value(normalSenior.getSeniorId().intValue()));
        mvc.perform(get(PATH).header("Authorization", bearer(otherChild)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].seniorId").value(7001))
                .andExpect(jsonPath("$.data[0].withdrawn").value(true));
    }

    @Test
    void parentJoinThenProfileCreationReturnsSeniorTableIdToChild() throws Exception {
        User child = user("onboarding-list-child", Role.CHILD);
        User parent = user("onboarding-list-parent", Role.PARENT);

        SeniorCodeCreateResponse familyResponse = familyService.createFamily(child);
        FamilyJoinRequest joinRequest = new FamilyJoinRequest();
        ReflectionTestUtils.setField(
                joinRequest,
                "seniorCode",
                familyResponse.getSeniorCode()
        );
        familyService.joinFamily(parent, joinRequest);

        SeniorCreateResponse created = seniorService.createSenior(
                child,
                new SeniorCreateRequest(
                        familyResponse.getFamilyId(),
                        "연결 시니어",
                        SeniorRelation.MOTHER,
                        null,
                        LocalDate.of(1950, 1, 1),
                        "010-1234-5678",
                        "서울시",
                        "101호",
                        37.5665,
                        126.9780
                )
        );
        entityManager.flush();
        entityManager.clear();

        mvc.perform(get(PATH).header("Authorization", bearer(child)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].familyId")
                        .value(familyResponse.getFamilyId().intValue()))
                .andExpect(jsonPath("$.data[0].seniorId")
                        .value(created.seniorId().intValue()))
                .andExpect(jsonPath("$.data[0].seniorProfileCompleted").value(true))
                .andExpect(jsonPath("$.data[0].withdrawn").value(false))
                .andExpect(jsonPath("$.data[0].withdrawalNoticeId").value(nullValue()));

        Senior persistedSenior = seniorRepository.findById(created.seniorId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(persistedSenior.getFamily().getFamilyId())
                .isEqualTo(familyResponse.getFamilyId());
        org.assertj.core.api.Assertions.assertThat(persistedSenior.getParentUser().getUsersId())
                .isEqualTo(parent.getUsersId());
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
        return familyRepository.save(Family.builder().seniorCode(seniorCode).build());
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
                .phoneNumber("01099998888")
                .family(family)
                .registeredBy(registeredBy)
                .parentUser(parent)
                .build());
    }

    private SeniorWithdrawalNotice notice(
            Long seniorId,
            Long familyId,
            String name,
            LocalDateTime withdrawnAt,
            LocalDateTime expiresAt
    ) {
        return noticeRepository.save(SeniorWithdrawalNotice.builder()
                .seniorId(seniorId)
                .familyId(familyId)
                .seniorName(name)
                .withdrawnAt(withdrawnAt)
                .expiresAt(expiresAt)
                .build());
    }

    private void recipient(
            SeniorWithdrawalNotice notice,
            User user,
            SeniorRelation relation,
            String customRelation,
            LocalDateTime acknowledgedAt
    ) {
        SeniorWithdrawalNoticeRecipient recipient =
                SeniorWithdrawalNoticeRecipient.builder()
                        .notice(notice)
                        .recipientUser(user)
                        .relation(relation)
                        .customRelation(customRelation)
                        .build();
        if (acknowledgedAt != null) {
            recipient.acknowledge(acknowledgedAt);
        }
        recipientRepository.save(recipient);
    }

    private String bearer(User user) {
        return "Bearer " + jwtUtil.createAccessToken(user);
    }
}
