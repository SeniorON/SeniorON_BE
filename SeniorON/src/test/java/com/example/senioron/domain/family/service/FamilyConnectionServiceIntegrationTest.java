package com.example.senioron.domain.family.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyMember;
import com.example.senioron.domain.family.repository.FamilyMemberRepository;
import com.example.senioron.domain.family.repository.FamilyRepository;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.repository.SeniorRepository;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest(showSql = false)
@Import(FamilyConnectionService.class)
class FamilyConnectionServiceIntegrationTest {

    @Autowired private FamilyConnectionService service;
    @Autowired private FamilyRepository familyRepository;
    @Autowired private FamilyMemberRepository familyMemberRepository;
    @Autowired private SeniorRepository seniorRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void subDisconnectDeletesOnlyOwnMembership() {
        Family family = saveFamily("SUB-LEAVE");
        User primary = saveUser("sub-leave-primary");
        User sub = saveUser("sub-leave-sub");
        saveMember(primary, family, ManagerType.PRIMARY);
        saveMember(sub, family, ManagerType.SUB);

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
    }

    @Test
    void primaryDisconnectLeavesFamilyAndSeniorForNextStage() {
        Family family = saveFamily("PRIMARY-LEAVE");
        User primary = saveUser("primary-leave");
        saveMember(primary, family, ManagerType.PRIMARY);
        Senior senior = seniorRepository.saveAndFlush(Senior.builder()
                .name("시니어")
                .birth(LocalDate.of(1950, 1, 1))
                .phoneNumber("01012345678")
                .family(family)
                .registeredBy(primary)
                .build());

        service.disconnect(primary, family.getFamilyId());
        entityManager.flush();
        entityManager.clear();

        Family persistedFamily = familyRepository.findById(family.getFamilyId())
                .orElseThrow();
        User persistedPrimary = userRepository.findById(primary.getUsersId())
                .orElseThrow();
        assertThat(familyMemberRepository.findByUserAndFamily(
                persistedPrimary,
                persistedFamily
        )).isEmpty();
        assertThat(seniorRepository.findById(senior.getSeniorId())).isPresent();
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
