package com.example.senioron.domain.family.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.senioron.domain.family.dto.request.FamilyUserReportRequest;
import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.entity.FamilyUserReport;
import com.example.senioron.domain.family.entity.PhotoReportReason;
import com.example.senioron.domain.family.entity.PhotoReportStatus;
import com.example.senioron.domain.family.repository.FamilyPhotoRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoSeniorRepository;
import com.example.senioron.domain.family.repository.FamilyUserReportRepository;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

class FamilyUserReportServiceTest {

    private final FamilyPhotoRepository familyPhotoRepository =
            mock(FamilyPhotoRepository.class);
    private final FamilyUserReportRepository familyUserReportRepository =
            mock(FamilyUserReportRepository.class);
    private final FamilyPhotoSeniorRepository familyPhotoSeniorRepository =
            mock(FamilyPhotoSeniorRepository.class);
    private final UserRepository userRepository =
            mock(UserRepository.class);

    private FamilyUserReportService familyUserReportService;

    private User reporter;
    private User uploader;
    private FamilyPhoto photo;

    @BeforeEach
    void setUp() {
        familyUserReportService = new FamilyUserReportService(
                familyPhotoRepository,
                familyUserReportRepository,
                familyPhotoSeniorRepository,
                userRepository
        );

        reporter = User.builder()
                .usersId(10L)
                .role(Role.PARENT)
                .build();
        uploader = User.builder()
                .usersId(20L)
                .role(Role.CHILD)
                .build();
        photo = FamilyPhoto.builder()
                .familyPhotoId(40L)
                .user(uploader)
                .imageKey("family/photo.jpg")
                .build();

        given(userRepository.findById(10L)).willReturn(Optional.of(reporter));
    }

    @Test
    void savesPendingReportAgainstUploaderWithImageKeySnapshot() {
        given(familyPhotoRepository.findAccessibleForReportByFamilyPhotoIdAndUser(40L, reporter))
                .willReturn(Optional.of(photo));

        familyUserReportService.reportUploader(
                reporter,
                40L,
                request(PhotoReportReason.ABUSE, "욕설이 담긴 사진을 계속 올려요.", true)
        );

        ArgumentCaptor<FamilyUserReport> captor =
                ArgumentCaptor.forClass(FamilyUserReport.class);
        verify(familyUserReportRepository).saveAndFlush(captor.capture());

        FamilyUserReport saved = captor.getValue();
        assertThat(saved.getReporter()).isSameAs(reporter);
        assertThat(saved.getReportedUser()).isSameAs(uploader);
        assertThat(saved.getFamilyPhoto()).isSameAs(photo);
        assertThat(saved.getImageKey()).isEqualTo("family/photo.jpg");
        assertThat(saved.getReason()).isEqualTo(PhotoReportReason.ABUSE);
        assertThat(saved.getDetail()).isEqualTo("욕설이 담긴 사진을 계속 올려요.");
        assertThat(saved.isHidden()).isTrue();
        assertThat(saved.getStatus()).isEqualTo(PhotoReportStatus.PENDING);
    }

    @Test
    void savesReporterPrimarySeniorWhenSeniorIdIsOmitted() {
        Senior senior = Senior.builder().seniorId(70L).build();
        given(familyPhotoRepository.findAccessibleForReportByFamilyPhotoIdAndUser(40L, reporter))
                .willReturn(Optional.of(photo));
        given(familyPhotoSeniorRepository.findReportContextSeniors(
                photo,
                reporter,
                ManagerType.PRIMARY,
                PageRequest.of(0, 1)
        )).willReturn(List.of(senior));

        familyUserReportService.reportUploader(
                reporter,
                40L,
                request(PhotoReportReason.ABUSE, null, true)
        );

        ArgumentCaptor<FamilyUserReport> captor =
                ArgumentCaptor.forClass(FamilyUserReport.class);
        verify(familyUserReportRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getSenior()).isSameAs(senior);
    }

    @Test
    void savesNullSeniorWhenNoSeniorIsLinked() {
        given(familyPhotoRepository.findAccessibleForReportByFamilyPhotoIdAndUser(40L, reporter))
                .willReturn(Optional.of(photo));
        given(familyPhotoSeniorRepository.findReportContextSeniors(any(), any(), any(), any()))
                .willReturn(List.of());

        familyUserReportService.reportUploader(
                reporter,
                40L,
                request(PhotoReportReason.ABUSE, null, true)
        );

        ArgumentCaptor<FamilyUserReport> captor =
                ArgumentCaptor.forClass(FamilyUserReport.class);
        verify(familyUserReportRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getSenior()).isNull();
    }

    @Test
    void savesSeniorFromReportScreenWhenSeniorIdIsGiven() {
        Senior viewingSenior = Senior.builder().seniorId(71L).build();
        given(familyPhotoRepository.findAccessibleForReportByFamilyPhotoIdAndUser(40L, reporter))
                .willReturn(Optional.of(photo));
        given(familyPhotoSeniorRepository.findReportContextSenior(photo, reporter, 71L))
                .willReturn(Optional.of(viewingSenior));

        FamilyUserReportRequest request = request(PhotoReportReason.ABUSE, null, true);
        request.setSeniorId(71L);

        familyUserReportService.reportUploader(reporter, 40L, request);

        ArgumentCaptor<FamilyUserReport> captor =
                ArgumentCaptor.forClass(FamilyUserReport.class);
        verify(familyUserReportRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getSenior()).isSameAs(viewingSenior);
        verify(familyPhotoSeniorRepository, never())
                .findReportContextSeniors(any(), any(), any(), any());
    }

    @Test
    void rejectsSeniorIdNotLinkedToPhotoAndReporter() {
        given(familyPhotoRepository.findAccessibleForReportByFamilyPhotoIdAndUser(40L, reporter))
                .willReturn(Optional.of(photo));
        given(familyPhotoSeniorRepository.findReportContextSenior(photo, reporter, 99L))
                .willReturn(Optional.empty());

        FamilyUserReportRequest request = request(PhotoReportReason.ABUSE, null, true);
        request.setSeniorId(99L);

        assertThatThrownBy(() -> familyUserReportService.reportUploader(reporter, 40L, request))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.FAMILY_PHOTO_NOT_FOUND);

        verify(familyUserReportRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsPhotoNotAccessibleToReporter() {
        given(familyPhotoRepository.findAccessibleForReportByFamilyPhotoIdAndUser(40L, reporter))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> familyUserReportService.reportUploader(
                reporter,
                40L,
                request(PhotoReportReason.ETC, null, false)
        ))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.FAMILY_PHOTO_NOT_FOUND);

        verify(familyUserReportRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsReportOnSelf() {
        FamilyPhoto ownPhoto = FamilyPhoto.builder()
                .familyPhotoId(41L)
                .user(reporter)
                .imageKey("family/own.jpg")
                .build();
        given(familyPhotoRepository.findAccessibleForReportByFamilyPhotoIdAndUser(41L, reporter))
                .willReturn(Optional.of(ownPhoto));

        assertThatThrownBy(() -> familyUserReportService.reportUploader(
                reporter,
                41L,
                request(PhotoReportReason.ETC, null, true)
        ))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.FAMILY_USER_REPORT_SELF);

        verify(familyUserReportRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsDuplicateReportOnSameUploader() {
        given(familyPhotoRepository.findAccessibleForReportByFamilyPhotoIdAndUser(40L, reporter))
                .willReturn(Optional.of(photo));
        given(familyUserReportRepository
                .existsByReportedUser_UsersIdAndReporter_UsersId(20L, 10L))
                .willReturn(true);

        assertThatThrownBy(() -> familyUserReportService.reportUploader(
                reporter,
                40L,
                request(PhotoReportReason.ABUSE, null, true)
        ))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.FAMILY_USER_ALREADY_REPORTED);

        verify(familyUserReportRepository, never()).saveAndFlush(any());
    }

    @Test
    void convertsConcurrentDuplicateInsertToAlreadyReported() {
        given(familyPhotoRepository.findAccessibleForReportByFamilyPhotoIdAndUser(40L, reporter))
                .willReturn(Optional.of(photo));
        given(familyUserReportRepository.saveAndFlush(any()))
                .willThrow(new DataIntegrityViolationException("uk_family_user_report_reported_reporter"));

        assertThatThrownBy(() -> familyUserReportService.reportUploader(
                reporter,
                40L,
                request(PhotoReportReason.ABUSE, null, true)
        ))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.FAMILY_USER_ALREADY_REPORTED);
    }

    private FamilyUserReportRequest request(
            PhotoReportReason reason,
            String detail,
            boolean hide
    ) {
        FamilyUserReportRequest request = new FamilyUserReportRequest();
        request.setReason(reason);
        request.setDetail(detail);
        request.setHide(hide);
        return request;
    }
}
