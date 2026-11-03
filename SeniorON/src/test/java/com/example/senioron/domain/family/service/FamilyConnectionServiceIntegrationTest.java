package com.example.senioron.domain.family.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import com.example.senioron.domain.event.entity.Event;
import com.example.senioron.domain.event.repository.EventRepository;
import com.example.senioron.domain.family.dto.response.FamilyConnectionDisconnectResponse;
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
import com.example.senioron.global.apiPayload.exception.BusinessException;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest(showSql = false)
@Import({FamilyConnectionService.class, UnusedFamilyCleanupService.class})
class FamilyConnectionServiceIntegrationTest {

    @Autowired private FamilyConnectionService service;
    @Autowired private FamilyRepository familyRepository;
    @Autowired private FamilyMemberRepository familyMemberRepository;
    @Autowired private SeniorRepository seniorRepository;
    @Autowired private SeniorPermissionSettingRepository permissionSettingRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void subDisconnectDeletesOnlyOwnMembership() {
        Family family = saveFamily("SUB-LEAVE");
        User primary = saveUser("sub-leave-primary");
        User sub = saveUser("sub-leave-sub");
        saveMember(primary, family, ManagerType.PRIMARY);
        saveMember(sub, family, ManagerType.SUB);

        FamilyConnectionDisconnectResponse response =
                service.disconnect(sub, family.getFamilyId());
        entityManager.flush();
        entityManager.clear();

        Family persistedFamily = familyRepository.findById(family.getFamilyId())
                .orElseThrow();
        User persistedPrimary = userRepository.findById(primary.getUsersId())
                .orElseThrow();
        User persistedSub = userRepository.findById(sub.getUsersId())
                .orElseThrow();
        assertThat(familyMemberRepository.findByUserAndFamily(
                persistedSub,
                persistedFamily
        )).isEmpty();
        assertThat(familyMemberRepository.findByUserAndFamily(
                persistedPrimary,
                persistedFamily
        )).isPresent();
        assertThat(response.familyId()).isEqualTo(family.getFamilyId());
        assertThat(response.familyDeleted()).isFalse();
    }

    @Test
    void primaryDisconnectDeletesUnusedFamilySeniorAndPermissionSetting() {
        Family family = saveFamily("PRIMARY-LEAVE");
        User primary = saveUser("primary-leave");
        FamilyMember member = saveMember(primary, family, ManagerType.PRIMARY);
        Senior senior = seniorRepository.saveAndFlush(Senior.builder()
                .name("시니어")
                .birth(LocalDate.of(1950, 1, 1))
                .phoneNumber("01012345678")
                .family(family)
                .registeredBy(primary)
                .build());
        permissionSettingRepository.saveAndFlush(
                SeniorPermissionSetting.builder()
                        .senior(senior)
                        .build()
        );

        FamilyConnectionDisconnectResponse response =
                service.disconnect(primary, family.getFamilyId());
        entityManager.clear();

        assertThat(familyMemberRepository.findById(member.getId())).isEmpty();
        assertThat(permissionSettingRepository.findById(senior.getSeniorId()))
                .isEmpty();
        assertThat(seniorRepository.findById(senior.getSeniorId())).isEmpty();
        assertThat(familyRepository.findById(family.getFamilyId())).isEmpty();
        assertThat(userRepository.findById(primary.getUsersId())).isPresent();
        assertThat(response.familyId()).isEqualTo(family.getFamilyId());
        assertThat(response.familyDeleted()).isTrue();
    }

    @Test
    void primaryDisconnectDeletesFamilyBeforeSeniorProfileCreation() {
        Family family = saveFamily("NO-SENIOR");
        User primary = saveUser("no-senior-primary");
        saveMember(primary, family, ManagerType.PRIMARY);

        FamilyConnectionDisconnectResponse response =
                service.disconnect(primary, family.getFamilyId());
        entityManager.clear();

        assertThat(familyRepository.findById(family.getFamilyId())).isEmpty();
        assertThat(userRepository.findById(primary.getUsersId())).isPresent();
        assertThat(response.familyId()).isEqualTo(family.getFamilyId());
        assertThat(response.familyDeleted()).isTrue();
    }

    @Test
    void seniorConnectionHistoryKeepsEntireFamily() {
        Family family = familyRepository.saveAndFlush(Family.builder()
                .seniorCode("HAS-HISTORY")
                .firstSeniorConnectedAt(LocalDateTime.of(2026, 11, 1, 10, 0))
                .build());
        User primary = saveUser("history-primary");
        FamilyMember member = saveMember(primary, family, ManagerType.PRIMARY);

        assertThatThrownBy(() -> service.disconnect(primary, family.getFamilyId()))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.PRIMARY_CONNECTION_DISCONNECT_HISTORY_EXISTS);

        assertThat(familyMemberRepository.findById(member.getId())).isPresent();
        assertThat(familyRepository.findById(family.getFamilyId())).isPresent();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void usageDataCleanupFailureRollsBackMembershipDeletion() {
        Family family = saveFamily("ROLLBACK");
        User primary = saveUser("rollback-primary");
        FamilyMember member = saveMember(primary, family, ManagerType.PRIMARY);
        Senior senior = seniorRepository.saveAndFlush(Senior.builder()
                .name("시니어")
                .birth(LocalDate.of(1950, 1, 1))
                .phoneNumber("01012345678")
                .family(family)
                .registeredBy(primary)
                .build());
        Event event = eventRepository.saveAndFlush(Event.builder()
                .senior(senior)
                .build());

        assertThatThrownBy(() -> service.disconnect(primary, family.getFamilyId()))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.UNUSED_FAMILY_CLEANUP_BLOCKED);

        assertThat(familyMemberRepository.findById(member.getId())).isPresent();
        assertThat(eventRepository.findById(event.getEventId())).isPresent();
        assertThat(seniorRepository.findById(senior.getSeniorId())).isPresent();
        assertThat(familyRepository.findById(family.getFamilyId())).isPresent();
    }

    private Family saveFamily(String seniorCode) {
        return familyRepository.saveAndFlush(Family.builder()
                .seniorCode(seniorCode)
                .build());
    }

    private User saveUser(String loginId) {
        return userRepository.saveAndFlush(User.builder()
                .loginId(loginId)
                .name(loginId)
                .role(Role.CHILD)
                .build());
    }

    private FamilyMember saveMember(
            User user,
            Family family,
            ManagerType managerType
    ) {
        return familyMemberRepository.saveAndFlush(FamilyMember.builder()
                .user(user)
                .family(family)
                .managerType(managerType)
                .build());
    }
}
