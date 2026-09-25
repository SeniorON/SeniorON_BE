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
import jakarta.persistence.EntityManager;
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

    @Autowired
    private EntityManager entityManager;

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
                .extracting(
                        "familyId",
                        "seniorId",
                        "seniorName",
                        "relation",
                        "customRelation",
                        "seniorProfileCompleted"
                )
                .containsExactly(tuple(
                        family.getFamilyId(),
                        senior.getSeniorId(),
                        "김영희",
                        SeniorRelation.MOTHER,
                        null,
                        true
                ));
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
                .extracting(
                        "familyId",
                        "seniorId",
                        "seniorName",
                        "relation",
                        "customRelation",
                        "seniorProfileCompleted"
                )
                .containsExactly(tuple(
                        family.getFamilyId(),
                        null,
                        null,
                        null,
                        null,
                        false
                ));
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
                .extracting(
                        "familyId",
                        "seniorId",
                        "seniorName",
                        "relation",
                        "customRelation",
                        "seniorProfileCompleted"
                )
                .containsExactly(
                        tuple(
                                firstFamily.getFamilyId(),
                                senior.getSeniorId(),
                                "김영희",
                                SeniorRelation.MOTHER,
                                null,
                                true
                        ),
                        tuple(secondFamily.getFamilyId(), null, null, null, null, false)
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
                .extracting(
                        "familyId",
                        "seniorId",
                        "seniorName",
                        "relation",
                        "customRelation",
                        "seniorProfileCompleted"
                )
                .containsExactly(
                        tuple(
                                firstFamily.getFamilyId(),
                                firstSenior.getSeniorId(),
                                "김영희",
                                SeniorRelation.MOTHER,
                                null,
                                true
                        ),
                        tuple(
                                secondFamily.getFamilyId(),
                                secondSenior.getSeniorId(),
                                "김영희",
                                SeniorRelation.MOTHER,
                                null,
                                true
                        )
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

    @Test
    void findsFamilyMembersWithFamilySeniorAndParentUser() {
        User child = saveUser("onboarding-child");
        User parent = saveUser("onboarding-parent", Role.PARENT);
        Family firstFamily = saveFamily("MANAGED7");
        Family secondFamily = saveFamily("MANAGED8");
        saveFamilyMember(child, firstFamily, ManagerType.PRIMARY);
        saveFamilyMember(child, secondFamily, ManagerType.SUB);
        Senior senior = saveSenior(firstFamily, child, parent);
        entityManager.flush();
        entityManager.clear();

        var result = familyMemberRepository.findAllByUserWithFamilyAndSeniorOrderByIdAsc(
                child
        );

        assertThat(result).hasSize(2);
        assertThat(result)
                .extracting(
                        member -> member.getFamily().getFamilyId(),
                        FamilyMember::getManagerType,
                        member -> member.getFamily().getSenior() == null
                                ? null
                                : member.getFamily().getSenior().getSeniorId(),
                        member -> member.getFamily().getSenior() == null
                                || member.getFamily().getSenior().getParentUser() == null
                                ? null
                                : member.getFamily().getSenior().getParentUser().getUsersId()
                )
                .containsExactly(
                        tuple(
                                firstFamily.getFamilyId(),
                                ManagerType.PRIMARY,
                                senior.getSeniorId(),
                                parent.getUsersId()
                        ),
                        tuple(secondFamily.getFamilyId(), ManagerType.SUB, null, null)
                );
    }

    private User saveUser(String key) {
        return saveUser(key, Role.CHILD);
    }

    private User saveUser(String key, Role role) {
        return userRepository.saveAndFlush(
                User.builder()
                        .loginId(key)
                        .email(key + "@example.com")
                        .name(key)
                        .role(role)
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
        saveFamilyMember(user, family, ManagerType.NONE);
    }

    private void saveFamilyMember(User user, Family family, ManagerType managerType) {
        familyMemberRepository.saveAndFlush(
                FamilyMember.builder()
                        .user(user)
                        .family(family)
                        .managerType(managerType)
                        .build()
        );
    }

    private Senior saveSenior(Family family, User user) {
        return saveSenior(family, user, null);
    }

    private Senior saveSenior(Family family, User user, User parentUser) {
        return seniorRepository.saveAndFlush(
                Senior.builder()
                        .name("김영희")
                        .relation(SeniorRelation.MOTHER)
                        .birth(LocalDate.of(1950, 1, 1))
                        .phoneNumber("01012345678")
                        .family(family)
                        .registeredBy(user)
                        .parentUser(parentUser)
                        .build()
        );
    }
}
