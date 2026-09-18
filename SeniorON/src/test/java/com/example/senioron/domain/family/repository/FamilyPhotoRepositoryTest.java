package com.example.senioron.domain.family.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyMember;
import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.entity.FamilyPhotoGroup;
import com.example.senioron.domain.family.entity.PhotoGroup;
import com.example.senioron.domain.family.entity.PhotoGroupFamily;
import com.example.senioron.domain.family.service.FamilyPhotoPermissionService;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
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
    private FamilyPhotoViewRepository familyPhotoViewRepository;

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
    void findsPhotoThroughNonRepresentativeMappedGroup() {
        Family currentFamily = saveFamily("FIRST-001");
        Family connectedFamily = saveFamily("CONNECTED-001");
        User currentUser = saveUser("current-user");
        User uploader = saveUser("uploader");

        saveFamilyMember(currentUser, currentFamily);
        saveFamilyMember(uploader, connectedFamily);

        PhotoGroup representativeGroup =
                savePhotoGroup("representative-group");
        PhotoGroup sharedGroup = savePhotoGroup("shared-group");
        savePhotoGroupFamily(connectedFamily, representativeGroup);
        savePhotoGroupFamily(currentFamily, sharedGroup);
        savePhotoGroupFamily(connectedFamily, sharedGroup);

        FamilyPhoto photo = savePhoto(
                representativeGroup,
                uploader,
                "connected-photo.jpg"
        );
        saveFamilyPhotoGroup(photo, representativeGroup);
        saveFamilyPhotoGroup(photo, sharedGroup);

        Optional<FamilyPhoto> result =
                familyPhotoRepository.findAccessibleByFamilyPhotoIdAndUser(
                        photo.getFamilyPhotoId(),
                        currentUser
                );

        assertThat(result).contains(photo);
    }

    @Test
    void keepsPreviouslySharedPhotoAccessibleAfterGroupIsDisconnected() {
        Family currentFamily = saveFamily("CURRENT-OLD");
        Family connectedFamily = saveFamily("CONNECTED-OLD");
        User currentUser = saveUser("current-old-user");
        User uploader = saveUser("old-photo-uploader");
        saveFamilyMember(currentUser, currentFamily);
        saveFamilyMember(uploader, connectedFamily);

        PhotoGroup sharedGroup = savePhotoGroup("disconnected-shared-group");
        savePhotoGroupFamily(currentFamily, sharedGroup);
        savePhotoGroupFamily(connectedFamily, sharedGroup);
        FamilyPhoto photo = savePhoto(
                sharedGroup,
                uploader,
                "previously-shared-photo.jpg"
        );
        saveFamilyPhotoGroup(photo, sharedGroup);

        sharedGroup.disconnect();
        photoGroupRepository.saveAndFlush(sharedGroup);

        assertThat(familyPhotoRepository.findAccessibleByFamilyPhotoIdAndUser(
                photo.getFamilyPhotoId(),
                currentUser
        )).contains(photo);
        assertThat(familyPhotoRepository.findAllAccessibleByFamily(
                currentFamily,
                PageRequest.of(0, 10)
        )).containsExactly(photo);
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
        saveFamilyPhotoGroup(photo, photoGroup);

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

    @Test
    void usesMappedGroupsForAlbumsAndFamilyHomeWithoutDuplicates() {
        Family currentFamily = saveFamily("CURRENT-003");
        Family connectedFamily = saveFamily("CONNECTED-004");
        User parent = saveUser("album-parent", Role.PARENT);
        User uploader = saveUser("shared-album-uploader");
        saveFamilyMember(uploader, connectedFamily);

        PhotoGroup representativeGroup =
                savePhotoGroup("album-representative-group");
        PhotoGroup firstSharedGroup =
                savePhotoGroup("album-first-shared-group");
        PhotoGroup secondSharedGroup =
                savePhotoGroup("album-second-shared-group");
        savePhotoGroupFamily(connectedFamily, representativeGroup);
        savePhotoGroupFamily(currentFamily, firstSharedGroup);
        savePhotoGroupFamily(connectedFamily, firstSharedGroup);
        savePhotoGroupFamily(currentFamily, secondSharedGroup);
        savePhotoGroupFamily(connectedFamily, secondSharedGroup);

        FamilyPhoto photo = savePhoto(
                representativeGroup,
                uploader,
                "shared-album-photo.jpg"
        );
        saveFamilyPhotoGroup(photo, representativeGroup);
        saveFamilyPhotoGroup(photo, firstSharedGroup);
        saveFamilyPhotoGroup(photo, secondSharedGroup);

        assertThat(familyPhotoRepository
                .findByFamilyOrderByCreatedAtDescFamilyPhotoIdDesc(
                        currentFamily,
                        PageRequest.of(0, 4)
                )).containsExactly(photo);
        assertThat(familyPhotoRepository.findRecentUploaders(
                currentFamily,
                PageRequest.of(0, 3)
        )).containsExactly(uploader);
        assertThat(familyPhotoRepository.findLatestPhotosByUploader(
                currentFamily,
                Role.CHILD
        )).containsExactly(photo);
        assertThat(familyPhotoRepository.countAlbumPhotosByUploader(
                currentFamily,
                Role.CHILD,
                LocalDateTime.now().minusDays(1),
                parent
        )).singleElement().satisfies(count -> {
            assertThat(count.getUploaderUserId())
                    .isEqualTo(uploader.getUsersId());
            assertThat(count.getPhotoCount()).isEqualTo(1L);
            assertThat(count.getNewPhotoCount()).isEqualTo(1L);
        });

        familyPhotoViewRepository.saveIfAbsent(photo, parent);
        familyPhotoViewRepository.flush();

        assertThat(familyPhotoRepository.countAlbumPhotosByUploader(
                currentFamily,
                Role.CHILD,
                LocalDateTime.now().minusDays(1),
                parent
        )).singleElement().satisfies(count -> {
            assertThat(count.getPhotoCount()).isEqualTo(1L);
            assertThat(count.getNewPhotoCount()).isZero();
        });
    }

    @Test
    void allowsPrimaryManagerToDeleteOnlyAfterUploaderLosesGroupAccess() {
        Family currentFamily = saveFamily("CURRENT-004");
        Family uploaderFamily = saveFamily("UPLOADER-001");
        User primaryManager = saveUser("primary-manager");
        User uploader = saveUser("orphan-photo-uploader");
        saveFamilyMember(
                primaryManager,
                currentFamily,
                ManagerType.PRIMARY
        );
        saveFamilyMember(uploader, uploaderFamily);

        PhotoGroup sharedGroup = savePhotoGroup("delete-shared-group");
        savePhotoGroupFamily(currentFamily, sharedGroup);
        savePhotoGroupFamily(uploaderFamily, sharedGroup);
        FamilyPhoto photo = savePhoto(
                sharedGroup,
                uploader,
                "delete-shared-photo.jpg"
        );
        saveFamilyPhotoGroup(photo, sharedGroup);

        FamilyPhotoPermissionService permissionService =
                new FamilyPhotoPermissionService(
                        familyPhotoGroupRepository
                );

        assertThat(permissionService.canDelete(photo, uploader)).isTrue();
        assertThat(permissionService.canDelete(photo, primaryManager))
                .isFalse();

        familyMemberRepository.deleteByUserAndFamily(
                uploader,
                uploaderFamily
        );
        familyMemberRepository.flush();

        assertThat(permissionService.canDelete(photo, primaryManager))
                .isTrue();
    }

    @Test
    void findsPhotoForFamilyThroughNonRepresentativeMappedGroup() {
        Family requestedFamily = saveFamily("VIEW-FAMILY-001");
        Family uploaderFamily = saveFamily("VIEW-UPLOADER-001");
        User uploader = saveUser("view-photo-uploader");

        saveFamilyMember(uploader, uploaderFamily);

        PhotoGroup representativeGroup =
                savePhotoGroup("view-representative-group");
        PhotoGroup sharedGroup =
                savePhotoGroup("view-shared-group");

        /*
         * 사진의 대표 그룹은 uploaderFamily에만 연결되어 있으므로
         * requestedFamily는 대표 그룹을 통해 사진에 접근할 수 없다.
         */
        savePhotoGroupFamily(
                uploaderFamily,
                representativeGroup
        );

        /*
         * 추가 공유 그룹은 두 Family에 연결한다.
         * requestedFamily는 이 매핑을 통해 사진에 접근할 수 있다.
         */
        savePhotoGroupFamily(
                uploaderFamily,
                sharedGroup
        );
        savePhotoGroupFamily(
                requestedFamily,
                sharedGroup
        );

        FamilyPhoto photo = savePhoto(
                representativeGroup,
                uploader,
                "view-shared-photo.jpg"
        );

        saveFamilyPhotoGroup(
                photo,
                representativeGroup
        );
        saveFamilyPhotoGroup(
                photo,
                sharedGroup
        );

        Optional<FamilyPhoto> result =
                familyPhotoRepository
                        .findAccessibleByFamilyPhotoIdAndFamily(
                                photo.getFamilyPhotoId(),
                                requestedFamily
                        );

        assertThat(result).contains(photo);
    }

    @Test
    void doesNotFindPhotoForFamilyWithoutMappedPhotoGroupAccess() {
        Family requestedFamily = saveFamily("VIEW-FAMILY-002");
        Family uploaderFamily = saveFamily("VIEW-UPLOADER-002");
        User uploader = saveUser("inaccessible-view-uploader");

        saveFamilyMember(uploader, uploaderFamily);

        PhotoGroup uploaderGroup =
                savePhotoGroup("inaccessible-view-group");
        PhotoGroup unrelatedGroup =
                savePhotoGroup("unrelated-family-group");

        savePhotoGroupFamily(
                uploaderFamily,
                uploaderGroup
        );
        savePhotoGroupFamily(
                requestedFamily,
                unrelatedGroup
        );

        FamilyPhoto photo = savePhoto(
                uploaderGroup,
                uploader,
                "inaccessible-view-photo.jpg"
        );

        saveFamilyPhotoGroup(
                photo,
                uploaderGroup
        );

        Optional<FamilyPhoto> result =
                familyPhotoRepository
                        .findAccessibleByFamilyPhotoIdAndFamily(
                                photo.getFamilyPhotoId(),
                                requestedFamily
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

    private void saveFamilyMember(User user, Family family) {
        saveFamilyMember(user, family, ManagerType.SUB);
    }

    private void saveFamilyMember(
            User user,
            Family family,
            ManagerType managerType
    ) {
        familyMemberRepository.saveAndFlush(
                FamilyMember.builder()
                        .user(user)
                        .family(family)
                        .managerType(managerType)
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
