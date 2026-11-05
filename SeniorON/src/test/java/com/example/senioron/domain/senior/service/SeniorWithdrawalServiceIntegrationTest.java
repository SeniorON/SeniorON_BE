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
    void noticeAndRecipientRemainAfterSeniorAndFamilyArePhysicallyDeleted() {
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
        entityManager.flush();
        entityManager.clear();

        service.withdrawParent(parent);
        entityManager.clear();

        assertThat(seniorRepository.findById(seniorId)).isEmpty();
        SeniorWithdrawalNotice notice = noticeRepository.findBySeniorId(seniorId).orElseThrow();
        assertThat(notice.getFamilyId()).isEqualTo(familyId);
        assertThat(notice.getSeniorName()).isEqualTo("김영희");
        assertThat(notice.getExpiresAt()).isEqualTo(notice.getWithdrawnAt().plusDays(30));

        assertThat(recipientRepository.findAll())
                .filteredOn(recipient -> recipient.getNotice()
                        .getSeniorWithdrawalNoticeId()
                        .equals(notice.getSeniorWithdrawalNoticeId()))
                .singleElement()
                .satisfies(recipient -> {
            assertThat(recipient.getNotice().getSeniorWithdrawalNoticeId())
                    .isEqualTo(notice.getSeniorWithdrawalNoticeId());
            assertThat(recipient.getRecipientUser().getUsersId()).isEqualTo(child.getUsersId());
            assertThat(recipient.getAcknowledgedAt()).isNull();
            assertThat(recipient.getRelation()).isEqualTo(SeniorRelation.MOTHER);
        });
        assertThat(familyRepository.findById(familyId)).isEmpty();
        assertThat(familyMemberRepository.findAll())
                .filteredOn(member -> member.getFamily().getFamilyId().equals(familyId))
                .isEmpty();
        assertThat(userRepository.findById(child.getUsersId())).isPresent();
        assertThat(userRepository.findById(parent.getUsersId())).isPresent();
    }

    @Test
    void deletesOnlyTargetFamilyWhenGuardiansBelongToMultipleFamilies() {
        User firstChild = userRepository.save(user("first-child", Role.CHILD));
        User secondChild = userRepository.save(user("second-child", Role.CHILD));
        User parent = userRepository.save(user("target-parent", Role.PARENT));
        Family targetFamily = familyRepository.save(
                Family.builder().seniorCode("TARGET-01").build()
        );
        Family otherFamily = familyRepository.save(
                Family.builder().seniorCode("OTHER-01").build()
        );
        familyMemberRepository.save(FamilyMember.builder()
                .user(firstChild)
                .family(targetFamily)
                .managerType(ManagerType.PRIMARY)
                .relation(SeniorRelation.MOTHER)
                .build());
        familyMemberRepository.save(FamilyMember.builder()
                .user(secondChild)
                .family(targetFamily)
                .managerType(ManagerType.SUB)
                .relation(SeniorRelation.OTHER)
                .customRelation("이모")
                .build());
        familyMemberRepository.save(FamilyMember.builder()
                .user(parent)
                .family(targetFamily)
                .managerType(ManagerType.NONE)
                .build());
        familyMemberRepository.save(FamilyMember.builder()
                .user(firstChild)
                .family(otherFamily)
                .managerType(ManagerType.PRIMARY)
                .relation(SeniorRelation.FATHER)
                .build());
        Senior targetSenior = seniorRepository.saveAndFlush(Senior.builder()
                .name("탈퇴 시니어")
                .birth(LocalDate.of(1950, 1, 1))
                .phoneNumber("010-0000-0000")
                .family(targetFamily)
                .registeredBy(firstChild)
                .parentUser(parent)
                .build());
        Long targetFamilyId = targetFamily.getFamilyId();
        Long otherFamilyId = otherFamily.getFamilyId();
        entityManager.flush();
        entityManager.clear();

        service.withdrawParent(parent);
        entityManager.clear();

        assertThat(seniorRepository.findById(targetSenior.getSeniorId())).isEmpty();
        assertThat(familyRepository.findById(targetFamilyId)).isEmpty();
        assertThat(familyRepository.findById(otherFamilyId)).isPresent();
        assertThat(familyMemberRepository.findAll())
                .filteredOn(member ->
                        member.getFamily().getFamilyId().equals(targetFamilyId)
                )
                .isEmpty();
        assertThat(familyMemberRepository.findAll())
                .filteredOn(member ->
                        member.getFamily().getFamilyId().equals(otherFamilyId)
                )
                .singleElement()
                .satisfies(member -> {
                    assertThat(member.getFamily().getFamilyId()).isEqualTo(otherFamilyId);
                    assertThat(member.getUser().getUsersId()).isEqualTo(firstChild.getUsersId());
                });
        assertThat(userRepository.findById(firstChild.getUsersId())).isPresent();
        assertThat(userRepository.findById(secondChild.getUsersId())).isPresent();
        SeniorWithdrawalNotice notice = noticeRepository
                .findBySeniorId(targetSenior.getSeniorId())
                .orElseThrow();
        assertThat(recipientRepository.findAll())
                .filteredOn(recipient -> recipient.getNotice()
                        .getSeniorWithdrawalNoticeId()
                        .equals(notice.getSeniorWithdrawalNoticeId()))
                .extracting(recipient -> recipient.getRecipientUser().getUsersId())
                .containsExactlyInAnyOrder(firstChild.getUsersId(), secondChild.getUsersId());
        assertThat(notice.getFamilyId()).isEqualTo(targetFamilyId);
    }

    @Test
    void deletesFamilyWithoutCreatingRecipientsWhenNoGuardianExists() {
        User registrar = userRepository.save(user("no-guardian-registrar", Role.CHILD));
        User parent = userRepository.save(user("no-guardian-parent", Role.PARENT));
        Family family = familyRepository.save(
                Family.builder().seniorCode("NO-GUARDIAN").build()
        );
        Senior senior = seniorRepository.saveAndFlush(Senior.builder()
                .name("보호자 없는 시니어")
                .birth(LocalDate.of(1950, 1, 1))
                .phoneNumber("010-0000-0000")
                .family(family)
                .registeredBy(registrar)
                .parentUser(parent)
                .build());
        Long familyId = family.getFamilyId();
        Long seniorId = senior.getSeniorId();
        entityManager.flush();
        entityManager.clear();

        service.withdrawParent(parent);
        entityManager.clear();

        assertThat(seniorRepository.findById(seniorId)).isEmpty();
        assertThat(familyRepository.findById(familyId)).isEmpty();
        SeniorWithdrawalNotice notice = noticeRepository.findBySeniorId(seniorId)
                .orElseThrow();
        assertThat(recipientRepository.findAll())
                .filteredOn(recipient -> recipient.getNotice()
                        .getSeniorWithdrawalNoticeId()
                        .equals(notice.getSeniorWithdrawalNoticeId()))
                .isEmpty();
        assertThat(notice.getCompletedAt()).isNotNull();
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
