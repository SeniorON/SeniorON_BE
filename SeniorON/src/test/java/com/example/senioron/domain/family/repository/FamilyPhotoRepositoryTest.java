package com.example.senioron.domain.family.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyMember;
import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.entity.PhotoGroup;
import com.example.senioron.domain.family.entity.PhotoGroupFamily;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class FamilyPhotoRepositoryTest {

    @Autowired
    private FamilyPhotoRepository familyPhotoRepository;

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
}
