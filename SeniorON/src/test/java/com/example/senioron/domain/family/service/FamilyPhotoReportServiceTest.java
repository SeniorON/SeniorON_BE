package com.example.senioron.domain.family.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.senioron.domain.family.dto.request.FamilyPhotoReportRequest;
import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.entity.FamilyPhotoReport;
import com.example.senioron.domain.family.entity.PhotoReportReason;
import com.example.senioron.domain.family.entity.PhotoReportStatus;
import com.example.senioron.domain.family.repository.FamilyPhotoReportRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoSeniorRepository;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

class FamilyPhotoReportServiceTest {

    private final FamilyPhotoRepository familyPhotoRepository =
            mock(FamilyPhotoRepository.class);
    private final FamilyPhotoReportRepository familyPhotoReportRepository =
            mock(FamilyPhotoReportRepository.class);
    private final FamilyPhotoSeniorRepository familyPhotoSeniorRepository =
            mock(FamilyPhotoSeniorRepository.class);
    private final UserRepository userRepository =
            mock(UserRepository.class);

    private FamilyPhotoReportService familyPhotoReportService;

    private User reporter;
    private User uploader;
    private FamilyPhoto photo;

    @BeforeEach
    void setUp() {
        familyPhotoReportService = new FamilyPhotoReportService(
                familyPhotoRepository,
                familyPhotoReportRepository,
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
    void savesPendingReportWithImageKeySnapshot() {
        given(familyPhotoRepository.findAccessibleForReportByFamilyPhotoIdAndUser(40L, reporter))
                .willReturn(Optional.of(photo));

        familyPhotoReportService.reportPhoto(
                reporter,
                40L,
                request(PhotoReportReason.ADVERTISEMENT, "광고 사진이에요.", true)
        );

        ArgumentCaptor<FamilyPhotoReport> captor =
                ArgumentCaptor.forClass(FamilyPhotoReport.class);
        verify(familyPhotoReportRepository).saveAndFlush(captor.capture());

        FamilyPhotoReport saved = captor.getValue();
        assertThat(saved.getFamilyPhoto()).isSameAs(photo);
        assertThat(saved.getReporter()).isSameAs(reporter);
        assertThat(saved.getReportedUser()).isSameAs(uploader);
        assertThat(saved.getImageKey()).isEqualTo("family/photo.jpg");
        assertThat(saved.getReason()).isEqualTo(PhotoReportReason.ADVERTISEMENT);
        assertThat(saved.getDetail()).isEqualTo("광고 사진이에요.");
        assertThat(saved.isHidden()).isTrue();
        assertThat(saved.getStatus()).isEqualTo(PhotoReportStatus.PENDING);
    }

    @Test
    void savesReporterSeniorAndPhotoUploadedAtSnapshot() {
        LocalDateTime uploadedAt = LocalDateTime.of(2026, 7, 12, 10, 21);
        ReflectionTestUtils.setField(photo, "createdAt", uploadedAt);
        Senior senior = Senior.builder().seniorId(70L).build();
        given(familyPhotoRepository.findAccessibleForReportByFamilyPhotoIdAndUser(40L, reporter))
                .willReturn(Optional.of(photo));
        given(familyPhotoSeniorRepository.findReportContextSeniors(
                eq(photo),
                eq(reporter),
                eq(ManagerType.PRIMARY),
                any(Pageable.class)
        )).willReturn(List.of(senior));

        familyPhotoReportService.reportPhoto(
                reporter,
                40L,
                request(PhotoReportReason.ETC, null, false)
        );

        ArgumentCaptor<FamilyPhotoReport> captor =
                ArgumentCaptor.forClass(FamilyPhotoReport.class);
        verify(familyPhotoReportRepository).saveAndFlush(captor.capture());

        assertThat(captor.getValue().getSenior()).isSameAs(senior);
        assertThat(captor.getValue().getPhotoUploadedAt()).isEqualTo(uploadedAt);
    }

    @Test
    void savesSeniorFromReportScreenWhenSeniorIdIsGiven() {
        Senior viewingSenior = Senior.builder().seniorId(71L).build();
        given(familyPhotoRepository.findAccessibleForReportByFamilyPhotoIdAndUser(40L, reporter))
                .willReturn(Optional.of(photo));
        given(familyPhotoSeniorRepository.findReportContextSenior(photo, reporter, 71L))
                .willReturn(Optional.of(viewingSenior));

        FamilyPhotoReportRequest request = request(PhotoReportReason.ETC, null, false);
        request.setSeniorId(71L);
        familyPhotoReportService.reportPhoto(reporter, 40L, request);

        ArgumentCaptor<FamilyPhotoReport> captor =
                ArgumentCaptor.forClass(FamilyPhotoReport.class);
        verify(familyPhotoReportRepository).saveAndFlush(captor.capture());

        assertThat(captor.getValue().getSenior()).isSameAs(viewingSenior);
        // 화면의 시니어가 오면 주담당자 우선 추정은 쓰지 않는다.
        verify(familyPhotoSeniorRepository, never())
                .findReportContextSeniors(any(), any(), any(), any());
    }

    @Test
    void rejectsSeniorIdNotLinkedToPhotoAndReporter() {
        given(familyPhotoRepository.findAccessibleForReportByFamilyPhotoIdAndUser(40L, reporter))
                .willReturn(Optional.of(photo));
        given(familyPhotoSeniorRepository.findReportContextSenior(photo, reporter, 99L))
                .willReturn(Optional.empty());

        FamilyPhotoReportRequest request = request(PhotoReportReason.ETC, null, false);
        request.setSeniorId(99L);

        assertThatThrownBy(() -> familyPhotoReportService.reportPhoto(reporter, 40L, request))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.FAMILY_PHOTO_NOT_FOUND);

        verify(familyPhotoReportRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsPhotoNotAccessibleToReporter() {
        given(familyPhotoRepository.findAccessibleForReportByFamilyPhotoIdAndUser(40L, reporter))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> familyPhotoReportService.reportPhoto(
                reporter,
                40L,
                request(PhotoReportReason.ETC, null, false)
        ))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.FAMILY_PHOTO_NOT_FOUND);

        verify(familyPhotoReportRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsReportOnOwnPhoto() {
        FamilyPhoto ownPhoto = FamilyPhoto.builder()
                .familyPhotoId(41L)
                .user(reporter)
                .imageKey("family/own.jpg")
                .build();
        given(familyPhotoRepository.findAccessibleForReportByFamilyPhotoIdAndUser(41L, reporter))
                .willReturn(Optional.of(ownPhoto));

        assertThatThrownBy(() -> familyPhotoReportService.reportPhoto(
                reporter,
                41L,
                request(PhotoReportReason.ETC, null, true)
        ))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.FAMILY_PHOTO_REPORT_OWN_PHOTO);

        verify(familyPhotoReportRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsDuplicateReport() {
        given(familyPhotoRepository.findAccessibleForReportByFamilyPhotoIdAndUser(40L, reporter))
                .willReturn(Optional.of(photo));
        given(familyPhotoReportRepository
                .existsByFamilyPhoto_FamilyPhotoIdAndReporter_UsersId(40L, 10L))
                .willReturn(true);

        assertThatThrownBy(() -> familyPhotoReportService.reportPhoto(
                reporter,
                40L,
                request(PhotoReportReason.ABUSE, null, false)
        ))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.FAMILY_PHOTO_ALREADY_REPORTED);

        verify(familyPhotoReportRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsRepeatedReportOnHiddenPhotoAsDuplicate() {
        // 숨김 신고한 사진은 접근 조회에서 제외되지만 404가 아니라 중복 신고로 응답한다.
        given(familyPhotoReportRepository
                .existsByFamilyPhoto_FamilyPhotoIdAndReporter_UsersId(40L, 10L))
                .willReturn(true);
        given(familyPhotoRepository.findAccessibleForReportByFamilyPhotoIdAndUser(40L, reporter))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> familyPhotoReportService.reportPhoto(
                reporter,
                40L,
                request(PhotoReportReason.ABUSE, null, true)
        ))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.FAMILY_PHOTO_ALREADY_REPORTED);
    }

    @Test
    void convertsConcurrentDuplicateInsertToAlreadyReported() {
        given(familyPhotoRepository.findAccessibleForReportByFamilyPhotoIdAndUser(40L, reporter))
                .willReturn(Optional.of(photo));
        given(familyPhotoReportRepository.saveAndFlush(any()))
                .willThrow(new DataIntegrityViolationException("uk_family_photo_report_photo_reporter"));

        assertThatThrownBy(() -> familyPhotoReportService.reportPhoto(
                reporter,
                40L,
                request(PhotoReportReason.ABUSE, null, true)
        ))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.FAMILY_PHOTO_ALREADY_REPORTED);
    }

    private FamilyPhotoReportRequest request(
            PhotoReportReason reason,
            String detail,
            boolean hide
    ) {
        FamilyPhotoReportRequest request = new FamilyPhotoReportRequest();
        request.setReason(reason);
        request.setDetail(detail);
        request.setHide(hide);
        return request;
    }
}
