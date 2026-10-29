package com.example.senioron.domain.family.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyMember;
import com.example.senioron.domain.family.entity.PhotoGroup;
import com.example.senioron.domain.family.entity.PhotoGroupFamily;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.repository.SeniorRepository;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import java.util.List;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class PhotoGroupFamilyRepositoryTest {

    @Autowired
    private FamilyRepository familyRepository;

    @Autowired
    private PhotoGroupRepository photoGroupRepository;

    @Autowired
    private PhotoGroupFamilyRepository photoGroupFamilyRepository;

    @Autowired
    private FamilyMemberRepository familyMemberRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SeniorRepository seniorRepository;

    @Test
    void excludesDisconnectedGroupFromUploadQueries() {
        Family currentFamily = saveFamily("CURRENT1");
        Family targetFamily = saveFamily("TARGET-1");
        PhotoGroup disconnectedGroup = savePhotoGroup("disconnected-group");
        savePhotoGroupFamily(currentFamily, disconnectedGroup);
        savePhotoGroupFamily(targetFamily, disconnectedGroup);
        disconnectedGroup.disconnect();
        photoGroupRepository.saveAndFlush(disconnectedGroup);

        assertThat(photoGroupFamilyRepository.findAllByFamilyAndPhotoGroupIds(
                currentFamily,
                List.of(disconnectedGroup.getId())
        )).isEmpty();
    }

    @Test
    void joiningFamiliesAloneDoesNotAllowCrossFamilyPhotoSharing() {
        User assistant = saveUser("child2");
        User primary = saveUser("child1");
        Family mother = saveSeniorFamily("MOTHER01", primary);
        Family father = saveSeniorFamily("FATHER01", primary);
        Family grandmother = saveSeniorFamily("GRANDM01", primary);
        saveFamilyMember(assistant, mother);
        saveFamilyMember(assistant, father);
        saveFamilyMember(primary, grandmother);
        PhotoGroupFamily motherLink = saveDefaultGroup(mother);
        PhotoGroupFamily fatherLink = saveDefaultGroup(father);
        PhotoGroupFamily grandmotherLink = saveDefaultGroup(grandmother);

        assertThat(photoGroupFamilyRepository.findManagedUploadGroupLinks(
                mother, assistant, List.of(motherLink.getPhotoGroup().getId(),
                        fatherLink.getPhotoGroup().getId(), grandmotherLink.getPhotoGroup().getId())
        )).containsExactly(motherLink);
        assertThat(photoGroupRepository.count()).isEqualTo(3);
    }

    @Test
    void sharedGroupsRequireMembershipInTheTargetFamilyForSubManagers() {
        User assistant = saveUser("child2");
        Family mother = saveSeniorFamily("MOTHER01", assistant);
        Family father = saveSeniorFamily("FATHER01", assistant);
        Family grandmother = saveSeniorFamily("GRANDM01", assistant);
        saveFamilyMember(assistant, mother);
        saveFamilyMember(assistant, father);
        PhotoGroupFamily motherLink = saveDefaultGroup(mother);
        PhotoGroupFamily fatherLink = saveDefaultGroup(father);
        PhotoGroupFamily grandmotherLink = saveDefaultGroup(grandmother);
        PhotoGroup motherFather = savePhotoGroup("mother-father");
        PhotoGroupFamily motherSharedLink = savePhotoGroupFamily(mother, motherFather);
        PhotoGroupFamily fatherSharedLink = savePhotoGroupFamily(father, motherFather);
        PhotoGroup motherGrandmother = savePhotoGroup("mother-grandmother");
        savePhotoGroupFamily(mother, motherGrandmother);
        savePhotoGroupFamily(grandmother, motherGrandmother);

        assertThat(photoGroupFamilyRepository.findManagedUploadGroupLinks(
                mother, assistant, List.of(motherLink.getPhotoGroup().getId(),
                        fatherLink.getPhotoGroup().getId(), grandmotherLink.getPhotoGroup().getId(),
                        motherFather.getId(), motherGrandmother.getId())
        )).containsExactly(motherLink, motherSharedLink);
    }

    @Test
    void managedQueriesExcludeDisconnectedSharedGroups() {
        User assistant = saveUser("child2");
        Family mother = saveSeniorFamily("MOTHER01", assistant);
        Family inactiveFamily = saveSeniorFamily("INACTIVE", assistant);
        saveFamilyMember(assistant, mother);
        saveFamilyMember(assistant, inactiveFamily);
        PhotoGroupFamily motherLink = saveDefaultGroup(mother);
        saveDefaultGroup(inactiveFamily);
        PhotoGroup sharedGroup = savePhotoGroup("disconnected-shared");
        savePhotoGroupFamily(mother, sharedGroup);
        savePhotoGroupFamily(inactiveFamily, sharedGroup);
        sharedGroup.disconnect();
        photoGroupRepository.flush();

        assertThat(photoGroupFamilyRepository.findManagedUploadGroupLinks(
                mother, assistant, List.of(motherLink.getPhotoGroup().getId(),
                        sharedGroup.getId())
        )).containsExactly(motherLink);
    }

    private Family saveSeniorFamily(String code, User registeredBy) {
        Family family = saveFamily(code);
        seniorRepository.saveAndFlush(Senior.builder()
                .name(code).family(family).registeredBy(registeredBy)
                .birth(LocalDate.of(1950, 1, 1)).phoneNumber("01012345678").build());
        return family;
    }

    private PhotoGroupFamily saveDefaultGroup(Family family) {
        return savePhotoGroupFamily(family, savePhotoGroup("default-" + family.getSeniorCode()));
    }

    private User saveUser(String loginId) {
        return userRepository.saveAndFlush(User.builder()
                .loginId(loginId)
                .email(loginId + "@example.com")
                .name(loginId)
                .role(Role.CHILD)
                .build());
    }

    private void saveFamilyMember(User user, Family family) {
        familyMemberRepository.saveAndFlush(FamilyMember.builder()
                .user(user)
                .family(family)
                .managerType(ManagerType.SUB)
                .build());
    }

    private Family saveFamily(String seniorCode) {
        return familyRepository.saveAndFlush(
                Family.builder()
                        .seniorCode(seniorCode)
                        .build()
        );
    }

    private PhotoGroup savePhotoGroup(String name) {
        return photoGroupRepository.saveAndFlush(
                PhotoGroup.builder()
                        .name(name)
                        .build()
        );
    }

    private PhotoGroupFamily savePhotoGroupFamily(
            Family family,
            PhotoGroup photoGroup
    ) {
        return photoGroupFamilyRepository.saveAndFlush(
                PhotoGroupFamily.builder()
                        .family(family)
                        .photoGroup(photoGroup)
                        .build()
        );
    }
}
