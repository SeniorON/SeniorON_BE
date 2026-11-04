package com.example.senioron.domain.senior.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.senioron.domain.senior.entity.SeniorRelation;
import com.example.senioron.domain.senior.entity.SeniorWithdrawalNotice;
import com.example.senioron.domain.senior.entity.SeniorWithdrawalNoticeRecipient;
import com.example.senioron.domain.senior.repository.SeniorWithdrawalNoticeRecipientRepository;
import com.example.senioron.domain.senior.repository.SeniorWithdrawalNoticeRepository;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.entity.UserStatus;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.jwt.JwtUtil;
import jakarta.persistence.EntityManager;
import jakarta.servlet.Filter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = {
        "cloud.aws.region=ap-northeast-2",
        "cloud.aws.s3.bucket=test-bucket"
})
@Transactional
class SeniorWithdrawalNoticeControllerIntegrationTest {

    private static final String PATH = "/api/seniors/withdrawal-notices";

    @Autowired private WebApplicationContext webContext;
    @Autowired @Qualifier("springSecurityFilterChain") private Filter securityFilter;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private UserRepository userRepository;
    @Autowired private SeniorWithdrawalNoticeRepository noticeRepository;
    @Autowired private SeniorWithdrawalNoticeRecipientRepository recipientRepository;
    @Autowired private EntityManager entityManager;

    private MockMvc mvc;
    private User child;
    private User otherChild;
    private User parent;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(webContext)
                .addFilters(securityFilter)
                .build();
        child = user("withdrawal-child", Role.CHILD);
        otherChild = user("withdrawal-other-child", Role.CHILD);
        parent = user("withdrawal-parent", Role.PARENT);
    }

    @Test
    void returnsOnlyCurrentChildUnacknowledgedAndUnexpiredNoticesNewestFirst()
            throws Exception {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        LocalDateTime recentWithdrawal = now.minusMinutes(5);
        LocalDateTime olderWithdrawal = now.minusDays(1);

        SeniorWithdrawalNotice recent = notice(
                101L, 11L, "최근 시니어", recentWithdrawal, now.plusDays(30));
        recipient(recent, child, SeniorRelation.OTHER, "이모", null);

        SeniorWithdrawalNotice older = notice(
                102L, 12L, "이전 시니어", olderWithdrawal, now.plusDays(29));
        recipient(older, child, SeniorRelation.MOTHER, null, null);

        SeniorWithdrawalNotice acknowledged = notice(
                103L, 13L, "확인한 시니어", now.minusMinutes(1), now.plusDays(30));
        recipient(acknowledged, child, SeniorRelation.FATHER, null, now.minusSeconds(10));

        SeniorWithdrawalNotice expired = notice(
                104L, 14L, "만료 시니어", now.minusDays(31), now.minusSeconds(1));
        recipient(expired, child, SeniorRelation.GRANDPARENT, null, null);

        SeniorWithdrawalNotice otherChildNotice = notice(
                105L, 15L, "다른 자녀 시니어", now.minusSeconds(30), now.plusDays(30));
        recipient(otherChildNotice, otherChild, SeniorRelation.MOTHER, null, null);

        entityManager.flush();
        entityManager.clear();

        mvc.perform(get(PATH).header("Authorization", bearer(child)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].withdrawalNoticeId")
                        .value(recent.getSeniorWithdrawalNoticeId().intValue()))
                .andExpect(jsonPath("$.data[0].familyId").value(11))
                .andExpect(jsonPath("$.data[0].seniorId").value(101))
                .andExpect(jsonPath("$.data[0].seniorName").value("최근 시니어"))
                .andExpect(jsonPath("$.data[0].relation").value("OTHER"))
                .andExpect(jsonPath("$.data[0].customRelation").value("이모"))
                .andExpect(jsonPath("$.data[0].withdrawnAt").value(
                        recentWithdrawal.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
                ))
                .andExpect(jsonPath("$.data[1].withdrawalNoticeId")
                        .value(older.getSeniorWithdrawalNoticeId().intValue()))
                .andExpect(jsonPath("$.data[1].seniorName").value("이전 시니어"))
                .andExpect(jsonPath("$.data[1].relation").value("MOTHER"))
                .andExpect(jsonPath("$.data[1].customRelation").value(nullValue()));
    }

    @Test
    void returnsEmptyArrayWhenChildHasNoUnacknowledgedNotice() throws Exception {
        mvc.perform(get(PATH).header("Authorization", bearer(child)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void parentCannotReadWithdrawalNotices() throws Exception {
        SeniorWithdrawalNotice notice = notice(
                201L,
                21L,
                "부모 접근 차단",
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusDays(30)
        );
        recipient(notice, parent, SeniorRelation.MOTHER, null, null);
        entityManager.flush();

        mvc.perform(get(PATH).header("Authorization", bearer(parent)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(
                        ErrorCode.SENIOR_WITHDRAWAL_NOTICE_CHILD_ONLY.getCode()
                ));
    }

    @Test
    void requestWithoutJwtIsRejected() throws Exception {
        mvc.perform(get(PATH))
                .andExpect(status().isUnauthorized());
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

    private SeniorWithdrawalNotice notice(
            Long seniorId,
            Long familyId,
            String seniorName,
            LocalDateTime withdrawnAt,
            LocalDateTime expiresAt
    ) {
        return noticeRepository.save(SeniorWithdrawalNotice.builder()
                .seniorId(seniorId)
                .familyId(familyId)
                .seniorName(seniorName)
                .withdrawnAt(withdrawnAt)
                .expiresAt(expiresAt)
                .build());
    }

    private SeniorWithdrawalNoticeRecipient recipient(
            SeniorWithdrawalNotice notice,
            User recipientUser,
            SeniorRelation relation,
            String customRelation,
            LocalDateTime acknowledgedAt
    ) {
        SeniorWithdrawalNoticeRecipient recipient = SeniorWithdrawalNoticeRecipient.builder()
                .notice(notice)
                .recipientUser(recipientUser)
                .relation(relation)
                .customRelation(customRelation)
                .build();
        if (acknowledgedAt != null) {
            recipient.acknowledge(acknowledgedAt);
        }
        return recipientRepository.save(recipient);
    }

    private String bearer(User user) {
        return "Bearer " + jwtUtil.createAccessToken(user);
    }
}
