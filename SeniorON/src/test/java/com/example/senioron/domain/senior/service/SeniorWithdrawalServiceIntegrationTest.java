package com.example.senioron.domain.senior.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.senioron.domain.device.entity.Device;
import com.example.senioron.domain.device.repository.DeviceRepository;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyMember;
import com.example.senioron.domain.family.repository.FamilyMemberRepository;
import com.example.senioron.domain.family.repository.FamilyRepository;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.entity.SeniorRelation;
import com.example.senioron.domain.senior.entity.SeniorWithdrawalNotice;
import com.example.senioron.domain.senior.repository.SeniorRepository;
import com.example.senioron.domain.senior.repository.SeniorWithdrawalDataCleanupRepository;
import com.example.senioron.domain.senior.repository.SeniorWithdrawalNoticeRecipientRepository;
import com.example.senioron.domain.senior.repository.SeniorWithdrawalNoticeRepository;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.entity.UserStatus;
import com.example.senioron.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest(showSql = false, properties = "senior.withdrawal-notice.retention-days=30")
@Import({SeniorWithdrawalService.class, SeniorWithdrawalDataCleanupRepository.class})
class SeniorWithdrawalServiceIntegrationTest {

    @Autowired private SeniorWithdrawalService service;
    @Autowired private UserRepository userRepository;
    @Autowired private FamilyRepository familyRepository;
    @Autowired private FamilyMemberRepository familyMemberRepository;
    @Autowired private SeniorRepository seniorRepository;
    @Autowired private DeviceRepository deviceRepository;
    @Autowired private SeniorWithdrawalNoticeRepository noticeRepository;
    @Autowired private SeniorWithdrawalNoticeRecipientRepository recipientRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void noticeAndRecipientRemainAfterSeniorIsPhysicallyDeleted() {
        User child = userRepository.save(user("child", Role.CHILD));
        User parent = userRepository.save(user("parent", Role.PARENT));
        Family family = familyRepository.save(Family.builder().seniorCode("ABCD-EFGH").build());
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
        Senior senior = seniorRepository.saveAndFlush(Senior.builder()
                .name("김영희")
                .birth(LocalDate.of(1950, 1, 1))
                .phoneNumber("010-0000-0000")
                .family(family)
                .registeredBy(child)
                .parentUser(parent)
                .build());
        deviceRepository.save(Device.builder()
                .user(child)
                .deviceIdentifier("child-device")
                .deviceToken("child-token")
                .build());
        Long seniorId = senior.getSeniorId();
        Long familyId = family.getFamilyId();

        service.withdrawParent(parent);
        entityManager.clear();

        assertThat(seniorRepository.findById(seniorId)).isEmpty();
        SeniorWithdrawalNotice notice = noticeRepository.findBySeniorId(seniorId).orElseThrow();
        assertThat(notice.getFamilyId()).isEqualTo(familyId);
        assertThat(notice.getSeniorName()).isEqualTo("김영희");
        assertThat(notice.getExpiresAt()).isEqualTo(notice.getWithdrawnAt().plusDays(30));

        assertThat(recipientRepository.findAll()).singleElement().satisfies(recipient -> {
            assertThat(recipient.getNotice().getSeniorWithdrawalNoticeId())
                    .isEqualTo(notice.getSeniorWithdrawalNoticeId());
            assertThat(recipient.getRecipientUser().getUsersId()).isEqualTo(child.getUsersId());
            assertThat(recipient.getAcknowledgedAt()).isNull();
            assertThat(recipient.getRelation()).isEqualTo(SeniorRelation.MOTHER);
        });
        assertThat(familyRepository.findById(familyId).orElseThrow().getSeniorCode()).isNull();
        assertThat(familyMemberRepository.findAllByFamilyOrderByIdAsc(family)).hasSize(2);
        assertThat(userRepository.findById(child.getUsersId())).isPresent();
        assertThat(userRepository.findById(parent.getUsersId())).isPresent();
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
}
