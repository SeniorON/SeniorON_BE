package com.example.senioron.domain.family.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyMember;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.entity.SeniorRelation;
import com.example.senioron.domain.senior.repository.SeniorRepository;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class FamilyMemberRepositoryTest {

    @Autowired
    private FamilyMemberRepository familyMemberRepository;

    @Autowired
    private FamilyRepository familyRepository;

    @Autowired
    private SeniorRepository seniorRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void findsFamilyAndSeniorWhenUserBelongsToFamilyWithSenior() {
        User user = saveUser("managed-one-senior");
        Family family = saveFamily("MANAGED1");
        saveFamilyMember(user, family);
        Senior senior = saveSenior(family, user);

        var result = familyMemberRepository.findManagedSeniorResponsesByUserId(
                user.getUsersId()
        );

        assertThat(result)
                .extracting("familyId", "seniorId")
                .containsExactly(tuple(family.getFamilyId(), senior.getSeniorId()));
    }

    @Test
    void findsFamilyWithNullSeniorIdWhenSeniorDoesNotExist() {
        User user = saveUser("managed-no-senior");
        Family family = saveFamily("MANAGED2");
        saveFamilyMember(user, family);

        var result = familyMemberRepository.findManagedSeniorResponsesByUserId(
                user.getUsersId()
        );

        assertThat(result)
                .extracting("familyId", "seniorId")
                .containsExactly(tuple(family.getFamilyId(), null));
    }

    @Test
    void findsEveryFamilyWhenOnlySomeFamiliesHaveSenior() {
        User user = saveUser("managed-mixed");
        Family firstFamily = saveFamily("MANAGED3");
        Family secondFamily = saveFamily("MANAGED4");
        saveFamilyMember(user, firstFamily);
        saveFamilyMember(user, secondFamily);
        Senior senior = saveSenior(firstFamily, user);

        var result = familyMemberRepository.findManagedSeniorResponsesByUserId(
                user.getUsersId()
        );

        assertThat(result)
                .extracting("familyId", "seniorId")
                .containsExactly(
                        tuple(firstFamily.getFamilyId(), senior.getSeniorId()),
                        tuple(secondFamily.getFamilyId(), null)
                );
    }

    @Test
    void findsEveryFamilyWhenAllFamiliesHaveSenior() {
        User user = saveUser("managed-all-seniors");
        Family firstFamily = saveFamily("MANAGED5");
        Family secondFamily = saveFamily("MANAGED6");
        saveFamilyMember(user, firstFamily);
        saveFamilyMember(user, secondFamily);
        Senior firstSenior = saveSenior(firstFamily, user);
        Senior secondSenior = saveSenior(secondFamily, user);

        var result = familyMemberRepository.findManagedSeniorResponsesByUserId(
                user.getUsersId()
        );

        assertThat(result)
                .extracting("familyId", "seniorId")
                .containsExactly(
                        tuple(firstFamily.getFamilyId(), firstSenior.getSeniorId()),
                        tuple(secondFamily.getFamilyId(), secondSenior.getSeniorId())
                );
    }

    @Test
    void returnsEmptyWhenUserHasNoFamily() {
        User user = saveUser("managed-empty");

        var result = familyMemberRepository.findManagedSeniorResponsesByUserId(
                user.getUsersId()
        );

        assertThat(result).isEmpty();
    }

    private User saveUser(String key) {
        return userRepository.saveAndFlush(
                User.builder()
                        .loginId(key)
                        .email(key + "@example.com")
                        .name(key)
                        .role(Role.CHILD)
                        .build()
        );
    }

    private Family saveFamily(String seniorCode) {
        return familyRepository.saveAndFlush(
                Family.builder()
                        .seniorCode(seniorCode)
                        .build()
        );
    }

    private void saveFamilyMember(User user, Family family) {
        familyMemberRepository.saveAndFlush(
                FamilyMember.builder()
                        .user(user)
                        .family(family)
                        .managerType(ManagerType.NONE)
                        .build()
        );
    }

    private Senior saveSenior(Family family, User user) {
        return seniorRepository.saveAndFlush(
                Senior.builder()
                        .name("김영희")
                        .relation(SeniorRelation.MOTHER)
                        .birth(LocalDate.of(1950, 1, 1))
                        .phoneNumber("01012345678")
                        .family(family)
                        .registeredBy(user)
                        .build()
        );
    }
}
