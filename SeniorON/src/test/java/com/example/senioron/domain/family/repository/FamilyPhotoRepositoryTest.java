package com.example.senioron.domain.family.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyMember;
import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.entity.FamilyPhotoGroup;
import com.example.senioron.domain.family.entity.FamilyPhotoSenior;
import com.example.senioron.domain.family.entity.PhotoGroup;
import com.example.senioron.domain.family.entity.PhotoGroupFamily;
import com.example.senioron.domain.family.service.FamilyPhotoPermissionService;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.entity.SeniorRelation;
import com.example.senioron.domain.senior.repository.SeniorRepository;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private FamilyPhotoSeniorRepository familyPhotoSeniorRepository;

    @Autowired
    private SeniorRepository seniorRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void findsPhotoSharedToSeniorOfUserFamily() {
        User currentUser = saveUser("current-user");
        User uploader = saveUser("uploader");
        Senior mother = saveSenior("MOTHER-001", uploader);
        Senior father = saveSenior("FATHER-001", uploader);
        saveFamilyMember(currentUser, mother.getFamily());

        FamilyPhoto photo = savePhoto(uploader, "shared-photo.jpg", mother, father);

        assertThat(familyPhotoRepository.findAccessibleByFamilyPhotoIdAndUser(
                photo.getFamilyPhotoId(),
                currentUser
        )).contains(photo);
        assertThat(familyPhotoRepository.findAccessibleByFamilyPhotoIdAndFamily(
                photo.getFamilyPhotoId(),
                mother.getFamily()
        )).contains(photo);
    }

    @Test
    void doesNotFindPhotoSharedOnlyToAnotherSenior() {
        User currentUser = saveUser("current-user");
        User uploader = saveUser("uploader");
        Senior mother = saveSenior("MOTHER-002", uploader);
        Senior father = saveSenior("FATHER-002", uploader);
        saveFamilyMember(currentUser, mother.getFamily());
        saveFamilyMember(uploader, mother.getFamily());

        // 같은 담당자가 올렸어도 공유하지 않은 시니어에게는 보이지 않는다.
        FamilyPhoto photo = savePhoto(uploader, "father-only-photo.jpg", father);

        assertThat(familyPhotoRepository.findAccessibleByFamilyPhotoIdAndUser(
                photo.getFamilyPhotoId(),
                currentUser
        )).isEmpty();
        assertThat(familyPhotoRepository.findAccessibleByFamilyPhotoIdAndFamily(
                photo.getFamilyPhotoId(),
                mother.getFamily()
        )).isEmpty();
        assertThat(familyPhotoRepository.findAllAccessibleByFamily(
                mother.getFamily(),
                PageRequest.of(0, 10)
        )).isEmpty();
        assertThat(familyPhotoRepository.countAccessibleByFamily(mother.getFamily()))
                .isZero();
    }

    @Test
    void findsPhotosSharedToSelectedSeniorWithoutDuplicates() {
        User firstUploader = saveUser("first-uploader");
        User secondUploader = saveUser("second-uploader");
        Senior mother = saveSenior("MOTHER-003", firstUploader);
        Senior father = saveSenior("FATHER-003", firstUploader);

        FamilyPhoto older = savePhoto(firstUploader, "older-photo.jpg", mother, father);
        FamilyPhoto newer = savePhoto(secondUploader, "newer-photo.jpg", mother);
        savePhoto(secondUploader, "father-only-photo.jpg", father);

        assertThat(familyPhotoRepository.findByFamilyOrderByCreatedAtDescFamilyPhotoIdDesc(
                mother.getFamily(),
                PageRequest.of(0, 4)
        )).containsExactly(newer, older);
        assertThat(familyPhotoRepository.findAllAccessibleByFamily(
                mother.getFamily(),
                PageRequest.of(0, 1)
        )).containsExactly(newer);
        assertThat(familyPhotoRepository.findNextAccessiblePageByFamily(
                mother.getFamily(),
                LocalDateTime.now().plusMinutes(1),
                Long.MAX_VALUE,
                PageRequest.of(0, 10)
        )).containsExactly(newer, older);
        assertThat(familyPhotoRepository.countAccessibleByFamily(mother.getFamily()))
                .isEqualTo(2L);
    }

    @Test
    void filtersSharedPhotosByUploader() {
        User firstUploader = saveUser("first-uploader");
        User secondUploader = saveUser("second-uploader");
        Senior mother = saveSenior("MOTHER-004", firstUploader);

        FamilyPhoto firstPhoto = savePhoto(firstUploader, "first-uploader-photo.jpg", mother);
        savePhoto(secondUploader, "second-uploader-photo.jpg", mother);

        assertThat(familyPhotoRepository.findAllAccessibleByFamilyAndUploader(
                mother.getFamily(),
                firstUploader,
                PageRequest.of(0, 10)
        )).containsExactly(firstPhoto);
        assertThat(familyPhotoRepository.countAccessibleByFamilyAndUploader(
                mother.getFamily(),
                firstUploader
        )).isEqualTo(1L);
    }

    @Test
    void countsAlbumsByUploaderForPhotosSharedToSenior() {
        User parent = saveUser("album-parent", Role.PARENT);
        User uploader = saveUser("album-uploader");
        Senior mother = saveSenior("MOTHER-005", uploader);
        Senior father = saveSenior("FATHER-005", uploader);

        FamilyPhoto photo = savePhoto(uploader, "album-photo.jpg", mother, father);
        savePhoto(uploader, "father-only-album-photo.jpg", father);

        assertThat(familyPhotoRepository.findLatestPhotosByUploader(
                mother.getFamily(),
                Role.CHILD
        )).containsExactly(photo);
        assertThat(familyPhotoRepository.countAlbumPhotosByUploader(
                mother.getFamily(),
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
                mother.getFamily(),
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
        FamilyPhoto photo = saveGroupPhoto(
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
    void findsMissingThumbnailsInBoundedIdOrderWithoutRepeatingEarlierPhotos() {
        User uploader = saveUser("thumbnail-backfill-uploader");
        PhotoGroup group = savePhotoGroup("thumbnail-backfill-group");
        FamilyPhoto first = saveGroupPhoto(group, uploader, "missing-first.jpg");
        familyPhotoRepository.saveAndFlush(FamilyPhoto.builder()
                .photoGroup(group)
                .user(uploader)
                .imageKey("already-generated.jpg")
                .thumbnailKey("thumbnails/already-generated.jpg")
                .build());
        FamilyPhoto second = saveGroupPhoto(group, uploader, "missing-second.jpg");
        FamilyPhoto third = saveGroupPhoto(group, uploader, "missing-third.jpg");

        assertThat(familyPhotoRepository.findPhotosWithoutThumbnail(
                0L, PageRequest.of(0, 2)
        )).extracting(FamilyPhoto::getFamilyPhotoId)
                .containsExactly(first.getFamilyPhotoId(), second.getFamilyPhotoId());

        // 앞선 사진 생성이 실패해 NULL로 남아 있어도 커서 이후로 진행한다.
        assertThat(familyPhotoRepository.findPhotosWithoutThumbnail(
                first.getFamilyPhotoId(), PageRequest.of(0, 2)
        )).extracting(FamilyPhoto::getFamilyPhotoId)
                .containsExactly(second.getFamilyPhotoId(), third.getFamilyPhotoId());

        assertThat(familyPhotoRepository.findPhotosWithoutThumbnail(
                third.getFamilyPhotoId(), PageRequest.of(0, 2)
        )).isEmpty();
    }

    @Test
    void attachesThumbnailOnlyToExistingPhotoWithoutThumbnail() {
        User uploader = saveUser("thumbnail-attachment-uploader");
        PhotoGroup group = savePhotoGroup("thumbnail-attachment-group");
        FamilyPhoto photo = saveGroupPhoto(group, uploader, "original-for-backfill.jpg");
        Long photoId = photo.getFamilyPhotoId();

        assertThat(familyPhotoRepository.updateThumbnailIfAbsent(
                photoId, "thumbnails/first.jpg"
        )).isEqualTo(1);
        assertThat(familyPhotoRepository.findById(photoId)).hasValueSatisfying(saved -> {
            assertThat(saved.getThumbnailKey()).isEqualTo("thumbnails/first.jpg");
            assertThat(saved.getImageKey()).isEqualTo("original-for-backfill.jpg");
        });

        assertThat(familyPhotoRepository.updateThumbnailIfAbsent(
                photoId, "thumbnails/second.jpg"
        )).isZero();
        assertThat(familyPhotoRepository.findById(photoId)).hasValueSatisfying(saved ->
                assertThat(saved.getThumbnailKey()).isEqualTo("thumbnails/first.jpg")
        );

        familyPhotoRepository.deleteById(photoId);
        familyPhotoRepository.flush();
        assertThat(familyPhotoRepository.updateThumbnailIfAbsent(
                photoId, "thumbnails/deleted-photo.jpg"
        )).isZero();
        assertThat(familyPhotoRepository.findById(photoId)).isEmpty();
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

    private Senior saveSenior(String seniorCode, User registeredBy) {
        Family family = saveFamily(seniorCode);
        return seniorRepository.saveAndFlush(
                Senior.builder()
                        .name(seniorCode)
                        .relation(SeniorRelation.OTHER)
                        .family(family)
                        .registeredBy(registeredBy)
                        .birth(LocalDate.of(1950, 1, 1))
                        .phoneNumber("01012345678")
                        .build()
        );
    }

    private FamilyPhoto savePhoto(User uploader, String imageKey, Senior... seniors) {
        PhotoGroup group = savePhotoGroup("group-" + imageKey);
        FamilyPhoto photo = saveGroupPhoto(group, uploader, imageKey);
        for (Senior senior : seniors) {
            familyPhotoSeniorRepository.saveAndFlush(
                    FamilyPhotoSenior.builder()
                            .familyPhoto(photo)
                            .senior(senior)
                            .build()
            );
        }
        return photo;
    }

    private FamilyPhoto saveGroupPhoto(
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
