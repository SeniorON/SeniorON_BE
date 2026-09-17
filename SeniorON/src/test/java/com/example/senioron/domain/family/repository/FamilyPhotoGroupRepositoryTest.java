package com.example.senioron.domain.family.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.entity.FamilyPhotoGroup;
import com.example.senioron.domain.family.entity.PhotoGroup;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class FamilyPhotoGroupRepositoryTest {

    @Autowired
    private FamilyPhotoRepository familyPhotoRepository;

    @Autowired
    private PhotoGroupRepository photoGroupRepository;

    @Autowired
    private FamilyPhotoGroupRepository familyPhotoGroupRepository;

    @Test
    void savesOnePhotoInMultiplePhotoGroups() {
        PhotoGroup firstGroup = savePhotoGroup("first-group");
        PhotoGroup secondGroup = savePhotoGroup("second-group");
        FamilyPhoto photo = savePhoto(firstGroup, "multi-group-photo.jpg");

        familyPhotoGroupRepository.saveAllAndFlush(
                java.util.List.of(
                        createMapping(photo, firstGroup),
                        createMapping(photo, secondGroup)
                )
        );

        assertThat(familyPhotoGroupRepository.findAll())
                .hasSize(2)
                .extracting(FamilyPhotoGroup::getPhotoGroup)
                .containsExactlyInAnyOrder(firstGroup, secondGroup);
    }

    @Test
    void rejectsDuplicatePhotoAndPhotoGroupMapping() {
        PhotoGroup photoGroup = savePhotoGroup("shared-group");
        FamilyPhoto photo = savePhoto(photoGroup, "duplicate-photo.jpg");

        familyPhotoGroupRepository.saveAndFlush(
                createMapping(photo, photoGroup)
        );

        assertThatThrownBy(() ->
                familyPhotoGroupRepository.saveAndFlush(
                        createMapping(photo, photoGroup)
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    private PhotoGroup savePhotoGroup(String name) {
        return photoGroupRepository.saveAndFlush(
                PhotoGroup.builder()
                        .name(name)
                        .build()
        );
    }

    private FamilyPhoto savePhoto(
            PhotoGroup photoGroup,
            String imageKey
    ) {
        return familyPhotoRepository.saveAndFlush(
                FamilyPhoto.builder()
                        .photoGroup(photoGroup)
                        .imageKey(imageKey)
                        .build()
        );
    }

    private FamilyPhotoGroup createMapping(
            FamilyPhoto photo,
            PhotoGroup photoGroup
    ) {
        return FamilyPhotoGroup.builder()
                .familyPhoto(photo)
                .photoGroup(photoGroup)
                .build();
    }
}
