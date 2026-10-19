package com.example.senioron.domain.family.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyMember;
import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.entity.FamilyPhotoGroup;
import com.example.senioron.domain.family.entity.PhotoGroup;
import com.example.senioron.domain.family.entity.PhotoGroupFamily;
import com.example.senioron.domain.family.repository.FamilyMemberRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoGroupRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoRepository;
import com.example.senioron.domain.family.repository.FamilyRepository;
import com.example.senioron.domain.family.repository.PhotoGroupFamilyRepository;
import com.example.senioron.domain.family.repository.PhotoGroupRepository;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Import(FamilyPhotoPermissionService.class)
class FamilyPhotoPermissionServiceTest {

    @Autowired private FamilyPhotoPermissionService permissionService;
    @Autowired private FamilyPhotoRepository familyPhotoRepository;
    @Autowired private FamilyPhotoGroupRepository familyPhotoGroupRepository;
    @Autowired private FamilyRepository familyRepository;
    @Autowired private FamilyMemberRepository familyMemberRepository;
    @Autowired private PhotoGroupRepository photoGroupRepository;
    @Autowired private PhotoGroupFamilyRepository photoGroupFamilyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManagerFactory entityManagerFactory;

    @Test
    void batchResultMatchesSinglePhotoPolicyAcrossUsersAndGroups() {
        Family currentFamily = saveFamily("CURRENT-BATCH");
        Family uploaderFamily = saveFamily("UPLOADER-BATCH");
        Family otherFamily = saveFamily("OTHER-BATCH");
        User primary = saveUser("batch-primary");
        User sub = saveUser("batch-sub");
        User activeUploader = saveUser("batch-active-uploader");
        User leftUploader = saveUser("batch-left-uploader");
        User otherFamilyUploader = saveUser("batch-other-family-uploader");
        saveFamilyMember(primary, currentFamily, ManagerType.PRIMARY);
        saveFamilyMember(sub, currentFamily, ManagerType.SUB);
        saveFamilyMember(activeUploader, uploaderFamily, ManagerType.SUB);
        saveFamilyMember(otherFamilyUploader, otherFamily, ManagerType.SUB);

        PhotoGroup sharedGroup = savePhotoGroup("batch-shared-group");
        savePhotoGroupFamily(currentFamily, sharedGroup);
        savePhotoGroupFamily(uploaderFamily, sharedGroup);
        PhotoGroup otherGroup = savePhotoGroup("batch-other-group");
        savePhotoGroupFamily(otherFamily, otherGroup);

        FamilyPhoto ownPhoto = savePhoto(primary, "own.jpg", sharedGroup);
        FamilyPhoto activeUploaderPhoto =
                savePhoto(activeUploader, "active-uploader.jpg", sharedGroup);
        FamilyPhoto leftUploaderPhoto =
                savePhoto(leftUploader, "left-uploader.jpg", sharedGroup);
        // 업로더가 다른 그룹(otherGroup)으로 여전히 접근 가능하므로 삭제 불가
        FamilyPhoto uploaderAccessibleViaOtherGroupPhoto =
                savePhoto(otherFamilyUploader, "multi-group.jpg", sharedGroup, otherGroup);
        // 업로더가 접근을 잃었지만 현재 사용자 가족과도 연결되지 않은 그룹의 사진
        FamilyPhoto unrelatedLeftUploaderPhoto =
                savePhoto(leftUploader, "unrelated.jpg", otherGroup);

        List<FamilyPhoto> photos = List.of(
                ownPhoto,
                activeUploaderPhoto,
                leftUploaderPhoto,
                uploaderAccessibleViaOtherGroupPhoto,
                unrelatedLeftUploaderPhoto
        );

        assertThat(permissionService.findDeletablePhotoIds(photos, primary))
                .containsExactlyInAnyOrder(
                        ownPhoto.getFamilyPhotoId(),
                        leftUploaderPhoto.getFamilyPhotoId()
                );
        assertThat(permissionService.findDeletablePhotoIds(photos, sub))
                .isEmpty();
        assertThat(permissionService.findDeletablePhotoIds(photos, activeUploader))
                .containsExactly(activeUploaderPhoto.getFamilyPhotoId());

        for (User user : List.of(primary, sub, activeUploader, leftUploader)) {
            assertThat(permissionService.findDeletablePhotoIds(photos, user))
                    .isEqualTo(deletableIdsBySinglePhotoCheck(photos, user));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 50})
    void permissionQueryCountStaysConstantRegardlessOfPhotoCount(int photoCount) {
        Family currentFamily = saveFamily("CURRENT-COUNT-" + photoCount);
        User primary = saveUser("count-primary-" + photoCount);
        saveFamilyMember(primary, currentFamily, ManagerType.PRIMARY);
        PhotoGroup sharedGroup = savePhotoGroup("count-group-" + photoCount);
        savePhotoGroupFamily(currentFamily, sharedGroup);

        // 업로더가 어느 가족에도 속하지 않아 사진마다 두 단계 확인이 모두 필요한 최악의 경우
        List<FamilyPhoto> photos = new ArrayList<>();
        for (int i = 0; i < photoCount; i++) {
            User leftUploader = saveUser("count-uploader-" + photoCount + "-" + i);
            photos.add(savePhoto(leftUploader, "count-" + photoCount + "-" + i + ".jpg", sharedGroup));
        }

        Statistics statistics = statistics();

        statistics.clear();
        Set<Long> deletablePhotoIds = permissionService.findDeletablePhotoIds(photos, primary);
        long batchQueryCount = statistics.getPrepareStatementCount();

        statistics.clear();
        Set<Long> singleCheckIds = deletableIdsBySinglePhotoCheck(photos, primary);
        long singleCheckQueryCount = statistics.getPrepareStatementCount();

        assertThat(deletablePhotoIds).isEqualTo(singleCheckIds).hasSize(photoCount);
        assertThat(batchQueryCount).isEqualTo(2);
        assertThat(singleCheckQueryCount).isEqualTo(2L * photoCount);
    }

    @Test
    void skipsPermissionQueriesWhenOnlyOwnPhotosOrNoPhotos() {
        Family family = saveFamily("OWN-ONLY");
        User uploader = saveUser("own-only-uploader");
        saveFamilyMember(uploader, family, ManagerType.PRIMARY);
        PhotoGroup group = savePhotoGroup("own-only-group");
        savePhotoGroupFamily(family, group);
        FamilyPhoto first = savePhoto(uploader, "own-1.jpg", group);
        FamilyPhoto second = savePhoto(uploader, "own-2.jpg", group);

        Statistics statistics = statistics();
        statistics.clear();

        assertThat(permissionService.findDeletablePhotoIds(List.of(first, second), uploader))
                .containsExactlyInAnyOrder(first.getFamilyPhotoId(), second.getFamilyPhotoId());
        assertThat(permissionService.findDeletablePhotoIds(List.of(), uploader)).isEmpty();
        assertThat(statistics.getPrepareStatementCount()).isZero();
    }

    private Set<Long> deletableIdsBySinglePhotoCheck(List<FamilyPhoto> photos, User user) {
        return photos.stream()
                .filter(photo -> permissionService.canDelete(photo, user))
                .map(FamilyPhoto::getFamilyPhotoId)
                .collect(Collectors.toSet());
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
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

    private void saveFamilyMember(User user, Family family, ManagerType managerType) {
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

    private void savePhotoGroupFamily(Family family, PhotoGroup photoGroup) {
        photoGroupFamilyRepository.saveAndFlush(
                PhotoGroupFamily.builder()
                        .family(family)
                        .photoGroup(photoGroup)
                        .build()
        );
    }

    private FamilyPhoto savePhoto(User uploader, String imageKey, PhotoGroup... photoGroups) {
        FamilyPhoto photo = familyPhotoRepository.saveAndFlush(
                FamilyPhoto.builder()
                        .photoGroup(photoGroups[0])
                        .user(uploader)
                        .imageKey(imageKey)
                        .build()
        );

        for (PhotoGroup photoGroup : photoGroups) {
            familyPhotoGroupRepository.saveAndFlush(
                    FamilyPhotoGroup.builder()
                            .familyPhoto(photo)
                            .photoGroup(photoGroup)
                            .build()
            );
        }

        return photo;
    }
}
