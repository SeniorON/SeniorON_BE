package com.example.senioron.domain.family.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyMember;
import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.entity.FamilyPhotoGroup;
import com.example.senioron.domain.family.entity.PhotoGroup;
import com.example.senioron.domain.family.entity.PhotoGroupFamily;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
class FamilyPhotoRepositoryTest {

    @Autowired
    private FamilyPhotoRepository familyPhotoRepository;

    @Autowired
    private FamilyPhotoGroupRepository familyPhotoGroupRepository;

    @Autowired
    private FamilyRepository familyRepository;

    @Autowired
    private FamilyMemberRepository familyMemberRepository;

    @Autowired
    private PhotoGroupRepository photoGroupRepository;

    @Autowired
    private PhotoGroupFamilyRepository photoGroupFamilyRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void findsPhotoWhenAnyUserFamilyIsConnectedToPhotoGroup() {
        Family firstFamily = saveFamily("FIRST-001");
        Family connectedFamily = saveFamily("CONNECTED-001");
        User currentUser = saveUser("current-user");
        User uploader = saveUser("uploader");

        saveFamilyMember(currentUser, firstFamily);
        saveFamilyMember(currentUser, connectedFamily);
        saveFamilyMember(uploader, connectedFamily);

        PhotoGroup photoGroup = savePhotoGroup("connected-group");
        savePhotoGroupFamily(connectedFamily, photoGroup);
        FamilyPhoto photo = savePhoto(photoGroup, uploader, "connected-photo.jpg");

        Optional<FamilyPhoto> result =
                familyPhotoRepository.findAccessibleByFamilyPhotoIdAndUser(
                        photo.getFamilyPhotoId(),
                        currentUser
                );

        assertThat(result).contains(photo);
    }

    @Test
    void doesNotFindPhotoWhenNoUserFamilyIsConnectedToPhotoGroup() {
        Family userFamily = saveFamily("USER-001");
        Family otherFamily = saveFamily("OTHER-001");
        User currentUser = saveUser("current-user");
        User uploader = saveUser("uploader");

        saveFamilyMember(currentUser, userFamily);
        saveFamilyMember(uploader, otherFamily);

        PhotoGroup photoGroup = savePhotoGroup("other-group");
        savePhotoGroupFamily(otherFamily, photoGroup);
        FamilyPhoto photo = savePhoto(photoGroup, uploader, "other-photo.jpg");

        Optional<FamilyPhoto> result =
                familyPhotoRepository.findAccessibleByFamilyPhotoIdAndUser(
                        photo.getFamilyPhotoId(),
                        currentUser
                );

        assertThat(result).isEmpty();
    }

    @Test
    void findsSharedPhotosWithoutDuplicatesAcrossPhotoGroups() {
        Family currentFamily = saveFamily("CURRENT-001");
        Family connectedFamily = saveFamily("CONNECTED-002");
        User uploader = saveUser("connected-uploader");
        saveFamilyMember(uploader, connectedFamily);

        PhotoGroup firstSharedGroup = savePhotoGroup("first-shared-group");
        PhotoGroup secondSharedGroup = savePhotoGroup("second-shared-group");
        PhotoGroup inaccessibleGroup = savePhotoGroup("inaccessible-group");

        savePhotoGroupFamily(currentFamily, firstSharedGroup);
        savePhotoGroupFamily(connectedFamily, firstSharedGroup);
        savePhotoGroupFamily(currentFamily, secondSharedGroup);
        savePhotoGroupFamily(connectedFamily, secondSharedGroup);
        savePhotoGroupFamily(connectedFamily, inaccessibleGroup);

        FamilyPhoto sharedPhoto = savePhoto(
                firstSharedGroup,
                uploader,
                "shared-photo.jpg"
        );
        saveFamilyPhotoGroup(sharedPhoto, firstSharedGroup);
        saveFamilyPhotoGroup(sharedPhoto, secondSharedGroup);

        FamilyPhoto inaccessiblePhoto = savePhoto(
                inaccessibleGroup,
                uploader,
                "inaccessible-photo.jpg"
        );
        saveFamilyPhotoGroup(inaccessiblePhoto, inaccessibleGroup);

        List<FamilyPhoto> result =
                familyPhotoRepository.findAllAccessibleByFamily(
                        currentFamily,
                        PageRequest.of(0, 10)
                );

        assertThat(result).containsExactly(sharedPhoto);
        assertThat(familyPhotoRepository.countAccessibleByFamily(currentFamily))
                .isEqualTo(1L);
    }

    @Test
    void filtersSharedPhotosByUploader() {
        Family currentFamily = saveFamily("CURRENT-002");
        Family connectedFamily = saveFamily("CONNECTED-003");
        User firstUploader = saveUser("first-uploader");
        User secondUploader = saveUser("second-uploader");
        saveFamilyMember(firstUploader, connectedFamily);
        saveFamilyMember(secondUploader, connectedFamily);

        PhotoGroup sharedGroup = savePhotoGroup("uploader-filter-group");
        savePhotoGroupFamily(currentFamily, sharedGroup);
        savePhotoGroupFamily(connectedFamily, sharedGroup);

        FamilyPhoto firstPhoto = savePhoto(
                sharedGroup,
                firstUploader,
                "first-uploader-photo.jpg"
        );
        FamilyPhoto secondPhoto = savePhoto(
                sharedGroup,
                secondUploader,
                "second-uploader-photo.jpg"
        );
        saveFamilyPhotoGroup(firstPhoto, sharedGroup);
        saveFamilyPhotoGroup(secondPhoto, sharedGroup);

        List<FamilyPhoto> result =
                familyPhotoRepository.findAllAccessibleByFamilyAndUploader(
                        currentFamily,
                        firstUploader,
                        PageRequest.of(0, 10)
                );

        assertThat(result).containsExactly(firstPhoto);
        assertThat(familyPhotoRepository.countAccessibleByFamilyAndUploader(
                currentFamily,
                firstUploader
        )).isEqualTo(1L);
    }

    private Family saveFamily(String seniorCode) {
        return familyRepository.saveAndFlush(
                Family.builder()
                        .seniorCode(seniorCode)
                        .build()
        );
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

    private void saveFamilyMember(User user, Family family) {
        familyMemberRepository.saveAndFlush(
                FamilyMember.builder()
                        .user(user)
                        .family(family)
                        .managerType(ManagerType.SUB)
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

    private void savePhotoGroupFamily(
            Family family,
            PhotoGroup photoGroup
    ) {
        photoGroupFamilyRepository.saveAndFlush(
                PhotoGroupFamily.builder()
                        .family(family)
                        .photoGroup(photoGroup)
                        .build()
        );
    }

    private FamilyPhoto savePhoto(
            PhotoGroup photoGroup,
            User uploader,
            String imageKey
    ) {
        return familyPhotoRepository.saveAndFlush(
                FamilyPhoto.builder()
                        .photoGroup(photoGroup)
                        .user(uploader)
                        .imageKey(imageKey)
                        .build()
        );
    }

    private void saveFamilyPhotoGroup(
            FamilyPhoto familyPhoto,
            PhotoGroup photoGroup
    ) {
        familyPhotoGroupRepository.saveAndFlush(
                FamilyPhotoGroup.builder()
                        .familyPhoto(familyPhoto)
                        .photoGroup(photoGroup)
                        .build()
        );
    }
}
