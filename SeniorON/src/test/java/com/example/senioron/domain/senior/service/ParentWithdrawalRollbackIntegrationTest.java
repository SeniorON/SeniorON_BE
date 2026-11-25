package com.example.senioron.domain.senior.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyMember;
import com.example.senioron.domain.family.repository.FamilyMemberRepository;
import com.example.senioron.domain.family.repository.FamilyRepository;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.entity.SeniorRelation;
import com.example.senioron.domain.senior.repository.SeniorRepository;
import com.example.senioron.domain.senior.repository.SeniorWithdrawalNoticeRepository;
import com.example.senioron.domain.user.dto.request.UserWithdrawalRequest;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.entity.UserStatus;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.domain.user.service.UserService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
        "cloud.aws.region=ap-northeast-2",
        "cloud.aws.s3.bucket=test-bucket"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ParentWithdrawalRollbackIntegrationTest {

    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @MockitoSpyBean private FamilyRepository familyRepository;
    @Autowired private FamilyMemberRepository familyMemberRepository;
    @Autowired private SeniorRepository seniorRepository;
    @Autowired private SeniorWithdrawalNoticeRepository noticeRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void rollsBackNoticeAndAllDeletesWhenFamilyDeleteFlushFails() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Fixture fixture = transaction.execute(status -> createFixture());
        if (fixture == null) {
            throw new IllegalStateException("테스트 픽스처 생성 실패");
        }

        doThrow(new DataIntegrityViolationException("forced family flush failure"))
                .when(familyRepository)
                .flush();

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            User parent = userRepository.findById(fixture.parentId()).orElseThrow();
            userService.withdraw(parent, withdrawalRequest());
        })).isInstanceOf(DataIntegrityViolationException.class);

        reset(familyRepository);

        transaction.executeWithoutResult(status -> {
            assertThat(familyRepository.findById(fixture.familyId())).isPresent();
            assertThat(seniorRepository.findById(fixture.seniorId())).isPresent();
            assertThat(familyMemberRepository.findAll())
                    .filteredOn(member -> member.getFamily()
                            .getFamilyId()
                            .equals(fixture.familyId()))
                    .hasSize(2);
            assertThat(noticeRepository.findBySeniorId(fixture.seniorId())).isEmpty();
            assertThat(userRepository.findById(fixture.parentId()))
                    .hasValueSatisfying(parent ->
                            assertThat(parent.getStatus()).isEqualTo(UserStatus.ACTIVE)
                    );
        });
    }

    private Fixture createFixture() {
        User child = userRepository.save(user("rollback-child", Role.CHILD));
        User parent = userRepository.save(user("rollback-parent", Role.PARENT));
        Family family = familyRepository.save(
                Family.builder().seniorCode("ROLLBACK-CODE").build()
        );
        familyMemberRepository.save(FamilyMember.builder()
                .user(child)
                .family(family)
                .managerType(ManagerType.PRIMARY)
                .relation(SeniorRelation.MOTHER)
                .build());
        familyMemberRepository.save(FamilyMember.builder()
                .user(parent)
                .family(family)
                .managerType(ManagerType.NONE)
                .build());
        Senior senior = seniorRepository.save(Senior.builder()
                .name("롤백 시니어")
                .birth(LocalDate.of(1950, 1, 1))
                .phoneNumber("010-0000-0000")
                .family(family)
                .registeredBy(child)
                .parentUser(parent)
                .build());
        return new Fixture(parent.getUsersId(), family.getFamilyId(), senior.getSeniorId());
    }

    private User user(String loginId, Role role) {
        return User.builder()
                .loginId(loginId)
                .email(loginId + "@example.com")
                .password("encoded")
                .name(loginId)
                .role(role)
                .status(UserStatus.ACTIVE)
                .build();
    }

    private UserWithdrawalRequest withdrawalRequest() {
        UserWithdrawalRequest request = new UserWithdrawalRequest();
        ReflectionTestUtils.setField(
                request,
                "confirmation",
                UserService.WITHDRAWAL_CONFIRMATION
        );
        return request;
    }

    private record Fixture(Long parentId, Long familyId, Long seniorId) {
    }
}
