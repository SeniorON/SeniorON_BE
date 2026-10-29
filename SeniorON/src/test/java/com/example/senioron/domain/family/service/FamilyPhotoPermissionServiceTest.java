package com.example.senioron.domain.family.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyMember;
import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.entity.FamilyPhotoSenior;
import com.example.senioron.domain.family.repository.FamilyMemberRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoSeniorRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoRepository;
import com.example.senioron.domain.family.repository.FamilyRepository;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.entity.SeniorRelation;
import com.example.senioron.domain.senior.repository.SeniorRepository;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
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
    @Autowired private FamilyPhotoSeniorRepository familyPhotoSeniorRepository;
    @Autowired private FamilyRepository familyRepository;
    @Autowired private FamilyMemberRepository familyMemberRepository;
    @Autowired private SeniorRepository seniorRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManagerFactory entityManagerFactory;

    @Test
    void batchResultMatchesSinglePhotoPolicyAcrossUsersAndSeniors() {
        User primary = saveUser("batch-primary");
        User sub = saveUser("batch-sub");
        User activeUploader = saveUser("batch-active-uploader");
        User leftUploader = saveUser("batch-left-uploader");
        Senior currentSenior = saveSenior("CURRENT-BATCH", primary);
        Senior otherSenior = saveSenior("OTHER-BATCH", primary);
        saveFamilyMember(primary, currentSenior.getFamily(), ManagerType.PRIMARY);
        saveFamilyMember(sub, currentSenior.getFamily(), ManagerType.SUB);
        saveFamilyMember(activeUploader, currentSenior.getFamily(), ManagerType.SUB);

        FamilyPhoto ownPhoto = savePhoto(primary, "own.jpg", currentSenior);
        FamilyPhoto activeUploaderPhoto =
                savePhoto(activeUploader, "active-uploader.jpg", currentSenior);
        FamilyPhoto leftUploaderPhoto =
                savePhoto(leftUploader, "left-uploader.jpg", currentSenior);
        // 주 담당자가 관리하는 시니어에게도 공유됐으므로 공유 해제 가능
        FamilyPhoto multiSeniorPhoto =
                savePhoto(activeUploader, "multi-senior.jpg", currentSenior, otherSenior);
        // 주 담당자가 관리하지 않는 시니어에게만 공유된 사진
        FamilyPhoto otherSeniorPhoto =
                savePhoto(leftUploader, "other-senior.jpg", otherSenior);

        List<FamilyPhoto> photos = List.of(
                ownPhoto,
                activeUploaderPhoto,
                leftUploaderPhoto,
                multiSeniorPhoto,
                otherSeniorPhoto
        );

        assertThat(permissionService.findDeletablePhotoIds(photos, primary))
                .containsExactlyInAnyOrder(
                        ownPhoto.getFamilyPhotoId(),
                        activeUploaderPhoto.getFamilyPhotoId(),
                        leftUploaderPhoto.getFamilyPhotoId(),
                        multiSeniorPhoto.getFamilyPhotoId()
                );
        assertThat(permissionService.findDeletablePhotoIds(photos, sub))
                .isEmpty();
        assertThat(permissionService.findDeletablePhotoIds(photos, activeUploader))
                .containsExactlyInAnyOrder(
                        activeUploaderPhoto.getFamilyPhotoId(),
                        multiSeniorPhoto.getFamilyPhotoId()
                );

        for (User user : List.of(primary, sub, activeUploader, leftUploader)) {
            assertThat(permissionService.findDeletablePhotoIds(photos, user))
                    .isEqualTo(deletableIdsBySinglePhotoCheck(photos, user));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 50})
    void permissionQueryCountStaysConstantRegardlessOfPhotoCount(int photoCount) {
        User primary = saveUser("count-primary-" + photoCount);
        Senior senior = saveSenior("CURRENT-COUNT-" + photoCount, primary);
        saveFamilyMember(primary, senior.getFamily(), ManagerType.PRIMARY);

        List<FamilyPhoto> photos = new ArrayList<>();
        for (int i = 0; i < photoCount; i++) {
            User uploader = saveUser("count-uploader-" + photoCount + "-" + i);
            photos.add(savePhoto(uploader, "count-" + photoCount + "-" + i + ".jpg", senior));
        }

        Statistics statistics = statistics();

        statistics.clear();
        Set<Long> deletablePhotoIds = permissionService.findDeletablePhotoIds(photos, primary);
        long batchQueryCount = statistics.getPrepareStatementCount();

        statistics.clear();
        Set<Long> singleCheckIds = deletableIdsBySinglePhotoCheck(photos, primary);
        long singleCheckQueryCount = statistics.getPrepareStatementCount();

        assertThat(deletablePhotoIds).isEqualTo(singleCheckIds).hasSize(photoCount);
        assertThat(batchQueryCount).isEqualTo(1);
        assertThat(singleCheckQueryCount).isEqualTo(photoCount);
    }

    @Test
    void skipsPermissionQueriesWhenOnlyOwnPhotosOrNoPhotos() {
        User uploader = saveUser("own-only-uploader");
        Senior senior = saveSenior("OWN-ONLY", uploader);
        saveFamilyMember(uploader, senior.getFamily(), ManagerType.PRIMARY);
        FamilyPhoto first = savePhoto(uploader, "own-1.jpg", senior);
        FamilyPhoto second = savePhoto(uploader, "own-2.jpg", senior);

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

    private Senior saveSenior(String seniorCode, User registeredBy) {
        Family family = familyRepository.saveAndFlush(
                Family.builder()
                        .seniorCode(seniorCode)
                        .build()
        );
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
        FamilyPhoto photo = familyPhotoRepository.saveAndFlush(
                FamilyPhoto.builder()
                        .user(uploader)
                        .imageKey(imageKey)
                        .build()
        );

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
}
