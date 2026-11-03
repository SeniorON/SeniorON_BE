package com.example.senioron.domain.family.service;

import com.example.senioron.domain.family.dto.request.FamilyUserReportRequest;
import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.entity.FamilyUserReport;
import com.example.senioron.domain.family.entity.PhotoReportStatus;
import com.example.senioron.domain.family.repository.FamilyPhotoRepository;
import com.example.senioron.domain.family.repository.FamilyUserReportRepository;
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
public class FamilyUserReportService {

    private final FamilyPhotoRepository familyPhotoRepository;
    private final FamilyUserReportRepository familyUserReportRepository;
    private final UserRepository userRepository;

    @Transactional
    public void reportUploader(
            User principal,
            Long familyPhotoId,
            FamilyUserReportRequest request
    ) {
        User currentUser = userRepository
                .findById(principal.getUsersId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 숨김 필터가 없는 조회라서, 이미 숨긴 게시자의 사진도 조회돼 중복 신고(409)로 응답할 수 있다.
        FamilyPhoto photo = familyPhotoRepository
                .findAccessibleForReportByFamilyPhotoIdAndUser(
                        familyPhotoId,
                        currentUser
                )
                .orElseThrow(() -> new BusinessException(ErrorCode.FAMILY_PHOTO_NOT_FOUND));

        User uploader = photo.getUser();
        if (uploader == null) {
            throw new BusinessException(ErrorCode.FAMILY_PHOTO_NOT_FOUND);
        }

        if (Objects.equals(uploader.getUsersId(), currentUser.getUsersId())) {
            throw new BusinessException(ErrorCode.FAMILY_USER_REPORT_SELF);
        }

        if (familyUserReportRepository.existsByReportedUser_UsersIdAndReporter_UsersId(
                uploader.getUsersId(),
                currentUser.getUsersId()
        )) {
            throw new BusinessException(ErrorCode.FAMILY_USER_ALREADY_REPORTED);
        }

        FamilyUserReport report = FamilyUserReport.builder()
                .reporter(currentUser)
                .reportedUser(uploader)
                .familyPhoto(photo)
                .imageKey(photo.getImageKey())
                .reason(request.getReason())
                .detail(request.getDetail())
                .hidden(request.getHide())
                .status(PhotoReportStatus.PENDING)
                .build();

        try {
            familyUserReportRepository.saveAndFlush(report);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.FAMILY_USER_ALREADY_REPORTED);
        }
    }
}