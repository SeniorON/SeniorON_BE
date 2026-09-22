package com.example.senioron.domain.family.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.entity.PhotoGroup;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class FamilyPhotoViewRepositoryTest {

    @Autowired
    private FamilyPhotoViewRepository familyPhotoViewRepository;

    @Autowired
    private FamilyPhotoRepository familyPhotoRepository;

    @Autowired
    private PhotoGroupRepository photoGroupRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void storesOneViewPerParentAndPhoto() {
        User uploader = saveUser("view-uploader", Role.CHILD);
        User firstParent = saveUser("first-view-parent", Role.PARENT);
        User secondParent = saveUser("second-view-parent", Role.PARENT);
        PhotoGroup photoGroup = photoGroupRepository.saveAndFlush(
                PhotoGroup.builder()
                        .name("view-test-group")
                        .build()
        );
        FamilyPhoto photo = familyPhotoRepository.saveAndFlush(
                FamilyPhoto.builder()
                        .photoGroup(photoGroup)
                        .user(uploader)
                        .imageKey("view-test-photo.jpg")
                        .build()
        );

        familyPhotoViewRepository.saveIfAbsent(
                photo,
                firstParent
        );
        familyPhotoViewRepository.saveIfAbsent(
                photo,
                firstParent
        );

        assertThat(familyPhotoViewRepository.count()).isEqualTo(1L);
        assertThat(familyPhotoViewRepository
                .existsByFamilyPhoto_FamilyPhotoIdAndParent_UsersId(
                        photo.getFamilyPhotoId(),
                        firstParent.getUsersId()
                )).isTrue();
        assertThat(familyPhotoViewRepository.findViewedFamilyPhotoIds(
                firstParent.getUsersId(),
                java.util.List.of(photo.getFamilyPhotoId())
        )).containsExactly(photo.getFamilyPhotoId());
        assertThat(familyPhotoViewRepository.findViewedFamilyPhotoIds(
                secondParent.getUsersId(),
                java.util.List.of(photo.getFamilyPhotoId())
        )).isEmpty();
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
}
