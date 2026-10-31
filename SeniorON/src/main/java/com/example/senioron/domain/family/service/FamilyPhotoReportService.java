package com.example.senioron.domain.family.service;

import com.example.senioron.domain.family.dto.request.FamilyPhotoReportRequest;
import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.entity.FamilyPhotoReport;
import com.example.senioron.domain.family.entity.PhotoReportStatus;
import com.example.senioron.domain.family.repository.FamilyPhotoReportRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoRepository;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FamilyPhotoReportService {

    private final FamilyPhotoRepository familyPhotoRepository;
    private final FamilyPhotoReportRepository familyPhotoReportRepository;
    private final UserRepository userRepository;

    @Transactional
    public void reportPhoto(
            User principal,
            Long familyPhotoId,
            FamilyPhotoReportRequest request
    ) {
        User currentUser = userRepository
                .findById(principal.getUsersId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 숨김 신고한 사진은 접근 조회에서 제외되므로 중복 신고를 먼저 확인한다.
        if (familyPhotoReportRepository.existsByFamilyPhoto_FamilyPhotoIdAndReporter_UsersId(
                familyPhotoId,
                currentUser.getUsersId()
        )) {
            throw new BusinessException(ErrorCode.FAMILY_PHOTO_ALREADY_REPORTED);
        }

        FamilyPhoto photo = familyPhotoRepository
                .findAccessibleByFamilyPhotoIdAndUser(
                        familyPhotoId,
                        currentUser
                )
                .orElseThrow(() -> new BusinessException(ErrorCode.FAMILY_PHOTO_NOT_FOUND));

        if (photo.getUser() != null
                && Objects.equals(photo.getUser().getUsersId(), currentUser.getUsersId())) {
            throw new BusinessException(ErrorCode.FAMILY_PHOTO_REPORT_OWN_PHOTO);
        }

        FamilyPhotoReport report = FamilyPhotoReport.builder()
                .familyPhoto(photo)
                .reporter(currentUser)
                .reportedUser(photo.getUser())
                .imageKey(photo.getImageKey())
                .reason(request.getReason())
                .detail(request.getDetail())
                .hidden(request.getHide())
                .status(PhotoReportStatus.PENDING)
                .build();

        try {
            familyPhotoReportRepository.saveAndFlush(report);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.FAMILY_PHOTO_ALREADY_REPORTED);
        }
    }
}