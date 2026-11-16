package com.example.senioron.domain.family.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import com.example.senioron.domain.family.dto.request.FamilyPhotoReportRequest;
import com.example.senioron.domain.family.dto.request.FamilyUserReportRequest;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyMember;
import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.entity.FamilyPhotoReport;
import com.example.senioron.domain.family.entity.FamilyPhotoSenior;
import com.example.senioron.domain.family.entity.FamilyUserReport;
import com.example.senioron.domain.family.entity.PhotoReportReason;
import com.example.senioron.domain.family.entity.PhotoReportStatus;
import com.example.senioron.domain.family.repository.FamilyPhotoReportRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoRepository;
import com.example.senioron.domain.family.repository.FamilyUserReportRepository;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@DataJpaTest(showSql = false)
@Import({FamilyPhotoReportService.class, FamilyUserReportService.class})
class FamilyReportIntegrationTest {

    @Autowired private EntityManager entityManager;
    @Autowired private FamilyPhotoReportService photoReportService;
    @Autowired private FamilyUserReportService userReportService;
    @Autowired private FamilyPhotoReportRepository photoReports;
    @Autowired private FamilyUserReportRepository userReports;
    @Autowired private FamilyPhotoRepository photos;

    private User reporter;
    private User otherReporter;
    private User uploader;
    private Family family;
    private Senior senior;
    private FamilyPhoto firstPhoto;
    private FamilyPhoto secondPhoto;

    @BeforeEach
    void setUp() {
        reporter = persist(User.builder().name("신고자").role(Role.CHILD).build());
        otherReporter = persist(User.builder().name("다른 신고자").role(Role.CHILD).build());
        uploader = persist(User.builder().name("게시자").role(Role.CHILD).build());
        family = persist(Family.builder().build());
        senior = persist(Senior.builder()
                .name("시니어")
                .birth(LocalDate.of(1950, 1, 1))
                .phoneNumber("01012345678")
                .family(family)
                .registeredBy(uploader)
                .build());
        for (User member : List.of(reporter, otherReporter)) {
            persist(FamilyMember.builder().family(family).user(member)
                    .managerType(ManagerType.SUB).build());
        }
        firstPhoto = saveSharedPhoto("repeat-report-first.jpg");
        secondPhoto = saveSharedPhoto("repeat-report-second.jpg");
        entityManager.flush();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void savesRepeatedPhotoReportsAndHidesOnlyWhenRequested(boolean hide) {
        photoReportService.reportPhoto(reporter, firstPhoto.getFamilyPhotoId(), photoRequest(false));
        photoReportService.reportPhoto(reporter, firstPhoto.getFamilyPhotoId(), photoRequest(false));
        photoReportService.reportPhoto(reporter, firstPhoto.getFamilyPhotoId(), photoRequest(hide));
        entityManager.clear();

        List<FamilyPhotoReport> reports = photoReports.findAll(Sort.by("id"));
        assertThat(reports).hasSize(3);
        assertThat(reports).extracting(FamilyPhotoReport::getId).doesNotHaveDuplicates();
        assertThat(reports).extracting(FamilyPhotoReport::isHidden).containsExactly(false, false, hide);
        assertThat(reports).allSatisfy(report -> {
            assertThat(report.getFamilyPhoto().getFamilyPhotoId()).isEqualTo(firstPhoto.getFamilyPhotoId());
            assertThat(report.getReporter().getUsersId()).isEqualTo(reporter.getUsersId());
            assertThat(report.getStatus()).isEqualTo(PhotoReportStatus.PENDING);
        });
        assertThat(photoReports.existsByFamilyPhoto_FamilyPhotoIdAndReporter_UsersIdAndHiddenTrue(
                firstPhoto.getFamilyPhotoId(), reporter.getUsersId())).isEqualTo(hide);
        assertThat(visiblePhotoIds(reporter)).contains(secondPhoto.getFamilyPhotoId());
        assertThat(visiblePhotoIds(reporter).contains(firstPhoto.getFamilyPhotoId())).isEqualTo(!hide);
        assertThat(visiblePhotoIds(otherReporter))
                .containsExactlyInAnyOrder(firstPhoto.getFamilyPhotoId(), secondPhoto.getFamilyPhotoId());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void savesRepeatedUploaderReportsAcrossPhotosAndHidesOnlyWhenRequested(boolean hide) {
        userReportService.reportUploader(reporter, firstPhoto.getFamilyPhotoId(), userRequest(false));
        userReportService.reportUploader(reporter, firstPhoto.getFamilyPhotoId(), userRequest(false));
        userReportService.reportUploader(reporter, secondPhoto.getFamilyPhotoId(), userRequest(hide));
        entityManager.clear();

        List<FamilyUserReport> reports = userReports.findAll(Sort.by("id"));
        assertThat(reports).hasSize(3);
        assertThat(reports).extracting(FamilyUserReport::getId).doesNotHaveDuplicates();
        assertThat(reports).extracting(FamilyUserReport::isHidden).containsExactly(false, false, hide);
        assertThat(reports).allSatisfy(report -> {
            assertThat(report.getReportedUser().getUsersId()).isEqualTo(uploader.getUsersId());
            assertThat(report.getReporter().getUsersId()).isEqualTo(reporter.getUsersId());
            assertThat(report.getStatus()).isEqualTo(PhotoReportStatus.PENDING);
        });
        assertThat(reports.get(2).getFamilyPhoto().getFamilyPhotoId()).isEqualTo(secondPhoto.getFamilyPhotoId());
        assertThat(userReports.existsByReportedUser_UsersIdAndReporter_UsersIdAndHiddenTrue(
                uploader.getUsersId(), reporter.getUsersId())).isEqualTo(hide);
        if (hide) {
            assertThat(visiblePhotoIds(reporter)).isEmpty();
        } else {
            assertThat(visiblePhotoIds(reporter))
                    .containsExactlyInAnyOrder(firstPhoto.getFamilyPhotoId(), secondPhoto.getFamilyPhotoId());
        }
        assertThat(visiblePhotoIds(otherReporter))
                .containsExactlyInAnyOrder(firstPhoto.getFamilyPhotoId(), secondPhoto.getFamilyPhotoId());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsFurtherPhotoReportsAfterHidingButAllowsAnotherReporter(boolean hide) {
        photoReportService.reportPhoto(reporter, firstPhoto.getFamilyPhotoId(), photoRequest(true));

        assertThatThrownBy(() -> photoReportService.reportPhoto(
                reporter, firstPhoto.getFamilyPhotoId(), photoRequest(hide)))
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.FAMILY_PHOTO_ALREADY_REPORTED);
        assertThat(photoReports.count()).isEqualTo(1);

        photoReportService.reportPhoto(otherReporter, firstPhoto.getFamilyPhotoId(), photoRequest(false));
        photoReportService.reportPhoto(reporter, secondPhoto.getFamilyPhotoId(), photoRequest(false));
        assertThat(photoReports.count()).isEqualTo(3);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsFurtherUploaderReportsFromAnotherPhotoAfterHiding(boolean hide) {
        userReportService.reportUploader(reporter, firstPhoto.getFamilyPhotoId(), userRequest(true));

        assertThatThrownBy(() -> userReportService.reportUploader(
                reporter, secondPhoto.getFamilyPhotoId(), userRequest(hide)))
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.FAMILY_USER_ALREADY_REPORTED);
        assertThat(userReports.count()).isEqualTo(1);

        userReportService.reportUploader(otherReporter, secondPhoto.getFamilyPhotoId(), userRequest(false));
        assertThat(userReports.count()).isEqualTo(2);
    }

    @Test
    void photoAndUploaderReportsHaveIndependentHistories() {
        photoReportService.reportPhoto(reporter, firstPhoto.getFamilyPhotoId(), photoRequest(false));
        userReportService.reportUploader(reporter, firstPhoto.getFamilyPhotoId(), userRequest(false));
        photoReportService.reportPhoto(reporter, firstPhoto.getFamilyPhotoId(), photoRequest(false));
        userReportService.reportUploader(reporter, firstPhoto.getFamilyPhotoId(), userRequest(false));

        assertThat(photoReports.count()).isEqualTo(2);
        assertThat(userReports.count()).isEqualTo(2);
        assertThat(visiblePhotoIds(reporter))
                .containsExactlyInAnyOrder(firstPhoto.getFamilyPhotoId(), secondPhoto.getFamilyPhotoId());
    }

    private FamilyPhotoReportRequest photoRequest(boolean hide) {
        FamilyPhotoReportRequest request = new FamilyPhotoReportRequest();
        request.setReason(PhotoReportReason.ETC);
        request.setHide(hide);
        request.setSeniorId(senior.getSeniorId());
        return request;
    }

    private FamilyUserReportRequest userRequest(boolean hide) {
        FamilyUserReportRequest request = new FamilyUserReportRequest();
        request.setReason(PhotoReportReason.ETC);
        request.setHide(hide);
        request.setSeniorId(senior.getSeniorId());
        return request;
    }

    private List<Long> visiblePhotoIds(User viewer) {
        return photos.findByFamilyOrderByCreatedAtDescFamilyPhotoIdDesc(family, viewer, PageRequest.of(0, 10))
                .stream().map(FamilyPhoto::getFamilyPhotoId).toList();
    }

    private FamilyPhoto saveSharedPhoto(String imageKey) {
        FamilyPhoto photo = persist(FamilyPhoto.builder().user(uploader).imageKey(imageKey).build());
        persist(FamilyPhotoSenior.builder().familyPhoto(photo).senior(senior).build());
        return photo;
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }
}
