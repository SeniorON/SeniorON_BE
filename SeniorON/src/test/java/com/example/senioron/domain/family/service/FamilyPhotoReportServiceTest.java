package com.example.senioron.domain.family.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.ArgumentMatchers.any;
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
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

class FamilyPhotoReportServiceTest {

    private final FamilyPhotoRepository familyPhotoRepository =
            mock(FamilyPhotoRepository.class);
    private final FamilyPhotoReportRepository familyPhotoReportRepository =
            mock(FamilyPhotoReportRepository.class);
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
        given(familyPhotoRepository.findAccessibleByFamilyPhotoIdAndUser(40L, reporter))
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
        assertThat(saved.getImageKey()).isEqualTo("family/photo.jpg");
        assertThat(saved.getReason()).isEqualTo(PhotoReportReason.ADVERTISEMENT);
        assertThat(saved.getDetail()).isEqualTo("광고 사진이에요.");
        assertThat(saved.isHidden()).isTrue();
        assertThat(saved.getStatus()).isEqualTo(PhotoReportStatus.PENDING);
    }

    @Test
    void rejectsPhotoNotAccessibleToReporter() {
        given(familyPhotoRepository.findAccessibleByFamilyPhotoIdAndUser(40L, reporter))
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
        given(familyPhotoRepository.findAccessibleByFamilyPhotoIdAndUser(41L, reporter))
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
        given(familyPhotoRepository.findAccessibleByFamilyPhotoIdAndUser(40L, reporter))
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
    void convertsConcurrentDuplicateInsertToAlreadyReported() {
        given(familyPhotoRepository.findAccessibleByFamilyPhotoIdAndUser(40L, reporter))
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
