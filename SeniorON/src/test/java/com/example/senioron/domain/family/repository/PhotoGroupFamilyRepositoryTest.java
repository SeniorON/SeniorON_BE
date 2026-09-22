package com.example.senioron.domain.family.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.PhotoGroup;
import com.example.senioron.domain.family.entity.PhotoGroupFamily;
import java.util.List;
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

    @Test
    void returnsTrueWhenFamiliesBelongToSamePhotoGroup() {
        Family currentFamily = saveFamily("CURRENT1");
        Family targetFamily = saveFamily("TARGET-1");
        PhotoGroup sharedGroup = savePhotoGroup("shared-group");
        savePhotoGroupFamily(currentFamily, sharedGroup);
        savePhotoGroupFamily(targetFamily, sharedGroup);

        boolean result = photoGroupFamilyRepository.existsSharedPhotoGroup(
                currentFamily,
                targetFamily
        );

        assertThat(result).isTrue();
    }

    @Test
    void returnsFalseWhenTargetSharesOnlyWithAnotherFamily() {
        Family currentFamily = saveFamily("CURRENT1");
        Family targetFamily = saveFamily("TARGET-1");
        Family anotherFamily = saveFamily("ANOTHER1");
        PhotoGroup currentGroup = savePhotoGroup("current-group");
        PhotoGroup otherSharedGroup = savePhotoGroup("other-shared-group");
        savePhotoGroupFamily(currentFamily, currentGroup);
        savePhotoGroupFamily(targetFamily, otherSharedGroup);
        savePhotoGroupFamily(anotherFamily, otherSharedGroup);

        boolean result = photoGroupFamilyRepository.existsSharedPhotoGroup(
                currentFamily,
                targetFamily
        );

        assertThat(result).isFalse();
    }

    @Test
    void findsOnlyFamiliesDirectlyConnectedThroughCurrentFamilyPhotoGroups() {
        Family currentFamily = saveFamily("CURRENT1");
        Family targetFamily = saveFamily("TARGET-1");
        Family indirectFamily = saveFamily("INDIRECT");
        PhotoGroup defaultGroup = savePhotoGroup("current-default");
        PhotoGroup directGroup = savePhotoGroup("current-target");
        PhotoGroup indirectGroup = savePhotoGroup("target-indirect");

        savePhotoGroupFamily(currentFamily, defaultGroup);
        savePhotoGroupFamily(currentFamily, directGroup);
        PhotoGroupFamily expectedLink =
                savePhotoGroupFamily(targetFamily, directGroup);
        savePhotoGroupFamily(targetFamily, indirectGroup);
        savePhotoGroupFamily(indirectFamily, indirectGroup);

        List<PhotoGroupFamily> result =
                photoGroupFamilyRepository.findConnectedFamilyLinks(
                        currentFamily
                );

        assertThat(result).containsExactly(expectedLink);
        assertThat(result.get(0).getPhotoGroup()).isEqualTo(directGroup);
        assertThat(result.get(0).getFamily()).isEqualTo(targetFamily);
    }

    @Test
    void excludesDisconnectedGroupFromConnectionAndUploadQueries() {
        Family currentFamily = saveFamily("CURRENT1");
        Family targetFamily = saveFamily("TARGET-1");
        PhotoGroup disconnectedGroup = savePhotoGroup("disconnected-group");
        savePhotoGroupFamily(currentFamily, disconnectedGroup);
        savePhotoGroupFamily(targetFamily, disconnectedGroup);
        disconnectedGroup.disconnect();
        photoGroupRepository.saveAndFlush(disconnectedGroup);

        assertThat(photoGroupFamilyRepository.existsSharedPhotoGroup(
                currentFamily,
                targetFamily
        )).isFalse();
        assertThat(photoGroupFamilyRepository.findConnectedFamilyLinks(
                currentFamily
        )).isEmpty();
        assertThat(photoGroupFamilyRepository.findAllByFamilyAndPhotoGroupIds(
                currentFamily,
                List.of(disconnectedGroup.getId())
        )).isEmpty();
        assertThat(photoGroupFamilyRepository.findAllSharedLinks(
                currentFamily,
                disconnectedGroup.getId()
        )).isEmpty();
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
