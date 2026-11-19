package com.example.senioron.domain.family.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyMember;
import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.entity.FamilyPhotoReport;
import com.example.senioron.domain.family.entity.FamilyPhotoSenior;
import com.example.senioron.domain.family.entity.FamilyPhotoView;
import com.example.senioron.domain.family.entity.FamilyUserReport;
import com.example.senioron.domain.family.entity.PhotoReportReason;
import com.example.senioron.domain.family.entity.PhotoReportStatus;
import com.example.senioron.domain.family.repository.FamilyMemberRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoReportRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoSeniorRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoViewRepository;
import com.example.senioron.domain.family.repository.FamilyRepository;
import com.example.senioron.domain.family.repository.FamilyUserReportRepository;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.repository.SeniorRepository;
import com.example.senioron.domain.user.dto.request.UserWithdrawalRequest;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.entity.UserStatus;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.domain.user.service.UserService;
import com.example.senioron.global.storage.S3Service;
import com.example.senioron.global.storage.S3DeletionService;
import com.example.senioron.global.storage.entity.S3DeletionTask;
import com.example.senioron.global.storage.repository.S3DeletionTaskRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
        "cloud.aws.region=ap-northeast-2",
        "cloud.aws.s3.bucket=test-bucket",
        "storage.s3-deletion.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:photo-withdrawal;MODE=MariaDB;DB_CLOSE_DELAY=-1"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FamilyPhotoWithdrawalIntegrationTest {

    @Autowired private UserService userService;
    @Autowired private UserRepository users;
    @Autowired private FamilyPhotoRepository photos;
    @Autowired private FamilyPhotoSeniorRepository shares;
    @Autowired private FamilyPhotoViewRepository views;
    @Autowired private FamilyPhotoReportRepository photoReports;
    @Autowired private FamilyUserReportRepository userReports;
    @Autowired private FamilyRepository families;
    @Autowired private FamilyMemberRepository members;
    @Autowired private SeniorRepository seniors;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private S3DeletionService deletionService;
    @Autowired private S3DeletionTaskRepository deletionTasks;
    @MockitoBean private S3Service s3;

    @Test
    void commitsWithdrawalAndDeletesAllUploadedPhotosIncludingPendingReportEvidence() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Fixture fixture = transaction.execute(status -> createFixture());
        when(s3.deleteObjects(org.mockito.ArgumentMatchers.anyList())).thenReturn(Map.of());

        transaction.executeWithoutResult(status -> {
            userService.withdraw(users.findById(fixture.userId()).orElseThrow(), request());
            photos.flush();
            verifyNoInteractions(s3);
        });

        // 커밋 후에도 요청 스레드에서는 파일을 삭제하지 않고 작업만 남는다.
        verifyNoInteractions(s3);
        assertThat(deletionTasks.findAll()).extracting(S3DeletionTask::getObjectKey)
                .containsExactlyInAnyOrderElementsOf(fixture.deletedKeys());
        assertThat(deletionService.processPendingBatch()).isEqualTo(fixture.deletedKeys().size());
        ArgumentCaptor<List<String>> deletedKeys = ArgumentCaptor.forClass(List.class);
        verify(s3).deleteObjects(deletedKeys.capture());
        assertThat(deletedKeys.getValue()).containsExactlyInAnyOrderElementsOf(fixture.deletedKeys());
        verifyNoMoreInteractions(s3);
        assertThat(deletionTasks.count()).isZero();
        transaction.executeWithoutResult(status -> {
            assertThat(users.findById(fixture.userId()).orElseThrow().getStatus())
                    .isEqualTo(UserStatus.WITHDRAWN);
            assertThat(photos.findById(fixture.photoId())).isEmpty();
            assertThat(photos.findById(fixture.unsharedPhotoId())).isEmpty();
            assertThat(photos.findById(fixture.otherPhotoId())).isPresent();
            assertThat(shares.findSeniorIdsByFamilyPhotoId(fixture.photoId())).isEmpty();
            assertThat(views.findById(fixture.viewId())).isEmpty();
            assertThat(photoReports.findById(fixture.photoReportId()))
                    .hasValueSatisfying(report -> {
                        assertThat(report.getFamilyPhoto()).isNull();
                        assertThat(report.getStatus()).isEqualTo(PhotoReportStatus.PENDING);
                    });
            assertThat(userReports.findById(fixture.userReportId()))
                    .hasValueSatisfying(report -> {
                        assertThat(report.getFamilyPhoto()).isNull();
                        assertThat(report.getStatus()).isEqualTo(PhotoReportStatus.PENDING);
                    });
            assertThat(photoReports.findById(fixture.retainedPhotoReportId())).isPresent();
            assertThat(userReports.findById(fixture.retainedUserReportId())).isPresent();
        });
    }

    @Test
    void rollbackRestoresPhotosSharesViewsAndReportReferencesWithoutDeletingFiles() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Fixture fixture = transaction.execute(status -> createFixture());

        transaction.executeWithoutResult(status -> {
            userService.withdraw(users.findById(fixture.userId()).orElseThrow(), request());
            photos.flush();
            status.setRollbackOnly();
        });

        verifyNoInteractions(s3);
        assertThat(deletionTasks.count()).isZero();
        transaction.executeWithoutResult(status -> {
            assertThat(users.findById(fixture.userId()).orElseThrow().getStatus())
                    .isEqualTo(UserStatus.ACTIVE);
            assertThat(photos.findById(fixture.photoId())).isPresent();
            assertThat(photos.findById(fixture.unsharedPhotoId())).isPresent();
            assertThat(shares.findSeniorIdsByFamilyPhotoId(fixture.photoId())).hasSize(2);
            assertThat(views.findById(fixture.viewId())).isPresent();
            assertThat(photoReports.findById(fixture.photoReportId()).orElseThrow()
                    .getFamilyPhoto()).isNotNull();
            assertThat(userReports.findById(fixture.userReportId()).orElseThrow()
                    .getFamilyPhoto()).isNotNull();
        });
    }

    private Fixture createFixture() {
        String suffix = UUID.randomUUID().toString();
        User owner = users.save(user("owner-" + suffix, Role.CHILD));
        User other = users.save(user("other-" + suffix, Role.CHILD));
        User parent = users.save(user("parent-" + suffix, Role.PARENT));
        Family first = families.save(Family.builder().seniorCode("A-" + suffix).build());
        Family second = families.save(Family.builder().seniorCode("B-" + suffix).build());
        members.save(FamilyMember.builder().user(owner).family(first)
                .managerType(ManagerType.SUB).build());
        Senior firstSenior = seniors.save(senior(first, other, "첫 시니어"));
        Senior secondSenior = seniors.save(senior(second, other, "둘째 시니어"));

        String original = suffix + "/original.jpg";
        String thumbnail = suffix + "/thumbnail.jpg";
        String unshared = suffix + "/unshared.jpg";
        String retainedPhoto = suffix + "/retained-photo.jpg";
        String retainedUser = suffix + "/retained-user.jpg";
        FamilyPhoto photo = photos.save(FamilyPhoto.builder()
                .user(owner).imageKey(original).thumbnailKey(thumbnail).build());
        FamilyPhoto unsharedPhoto = photos.save(FamilyPhoto.builder()
                .user(owner).imageKey(unshared).build());
        FamilyPhoto otherPhoto = photos.save(FamilyPhoto.builder()
                .user(other).imageKey(suffix + "/other.jpg").build());
        shares.save(FamilyPhotoSenior.builder().familyPhoto(photo).senior(firstSenior).build());
        shares.save(FamilyPhotoSenior.builder().familyPhoto(photo).senior(secondSenior).build());
        FamilyPhotoView view = views.save(FamilyPhotoView.builder()
                .familyPhoto(photo).parent(parent).build());
        FamilyPhotoReport photoReport = photoReports.save(photoReport(photo, original, other, owner));
        FamilyUserReport userReport = userReports.save(userReport(photo, original, other, owner));
        FamilyPhotoReport retainedPhotoReport = photoReports.save(
                photoReport(null, retainedPhoto, other, owner));
        FamilyUserReport retainedUserReport = userReports.save(
                userReport(null, retainedUser, parent, owner));
        entityManager.flush();
        entityManager.clear();
        return new Fixture(owner.getUsersId(), photo.getFamilyPhotoId(),
                unsharedPhoto.getFamilyPhotoId(), otherPhoto.getFamilyPhotoId(), view.getId(),
                photoReport.getId(), userReport.getId(), retainedPhotoReport.getId(),
                retainedUserReport.getId(),
                List.of(original, thumbnail, unshared, retainedPhoto, retainedUser));
    }

    private User user(String loginId, Role role) {
        return User.builder().loginId(loginId).email(loginId + "@example.com")
                .password("encoded").name("담당자").role(role).status(UserStatus.ACTIVE).build();
    }

    private Senior senior(Family family, User registeredBy, String name) {
        return Senior.builder().name(name).birth(LocalDate.of(1950, 1, 1))
                .phoneNumber("010-0000-0000").family(family).registeredBy(registeredBy).build();
    }

    private FamilyPhotoReport photoReport(FamilyPhoto photo, String key, User reporter, User owner) {
        return FamilyPhotoReport.builder().familyPhoto(photo).imageKey(key)
                .reporter(reporter).reportedUser(owner).reason(PhotoReportReason.ETC)
                .status(PhotoReportStatus.PENDING).build();
    }

    private FamilyUserReport userReport(FamilyPhoto photo, String key, User reporter, User owner) {
        return FamilyUserReport.builder().familyPhoto(photo).imageKey(key)
                .reporter(reporter).reportedUser(owner).reason(PhotoReportReason.ETC)
                .status(PhotoReportStatus.PENDING).build();
    }

    private UserWithdrawalRequest request() {
        UserWithdrawalRequest request = new UserWithdrawalRequest();
        ReflectionTestUtils.setField(request, "confirmation", UserService.WITHDRAWAL_CONFIRMATION);
        return request;
    }

    private record Fixture(Long userId, Long photoId, Long unsharedPhotoId, Long otherPhotoId,
                           Long viewId, Long photoReportId, Long userReportId,
                           Long retainedPhotoReportId, Long retainedUserReportId,
                           List<String> deletedKeys) {
    }
}
