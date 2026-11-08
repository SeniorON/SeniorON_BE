package com.example.senioron.domain.family.service;

import com.example.senioron.domain.family.dto.request.FamilyPhotoReportRequest;
import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.entity.FamilyPhotoReport;
import com.example.senioron.domain.family.entity.PhotoReportStatus;
import com.example.senioron.domain.family.repository.FamilyPhotoReportRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoSeniorRepository;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FamilyPhotoReportService {

    private final FamilyPhotoRepository familyPhotoRepository;
    private final FamilyPhotoReportRepository familyPhotoReportRepository;
    private final FamilyPhotoSeniorRepository familyPhotoSeniorRepository;
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
                .findAccessibleForReportByFamilyPhotoIdAndUser(
                        familyPhotoId,
                        currentUser
                )
                .orElseThrow(() -> new BusinessException(ErrorCode.FAMILY_PHOTO_NOT_FOUND));

        if (photo.getUser() != null
                && Objects.equals(photo.getUser().getUsersId(), currentUser.getUsersId())) {
            throw new BusinessException(ErrorCode.FAMILY_PHOTO_REPORT_OWN_PHOTO);
        }

        Senior contextSenior = resolveContextSenior(photo, currentUser, request.getSeniorId());

        FamilyPhotoReport report = FamilyPhotoReport.builder()
                .familyPhoto(photo)
                .reporter(currentUser)
                .reportedUser(photo.getUser())
                .senior(contextSenior)
                .photoUploadedAt(photo.getCreatedAt())
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

    // 사진이 여러 시니어에게 공유될 수 있어, 관리자 상세의 시니어 코드·역할 기준이 될 시니어를 정한다.
    private Senior resolveContextSenior(FamilyPhoto photo, User reporter, Long seniorId) {
        if (seniorId != null) {
            // 신고 화면에서 보던 시니어를 그대로 쓰되, 접근할 수 없는 시니어면 사진이 없는 것으로 응답한다.
            return familyPhotoSeniorRepository
                    .findReportContextSenior(photo, reporter, seniorId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.FAMILY_PHOTO_NOT_FOUND));
        }

        // seniorId를 보내지 않는 구버전 앱·단건 진입은 신고자가 주담당자인 시니어를 우선 사용한다.
        return familyPhotoSeniorRepository
                .findReportContextSeniors(
                        photo,
                        reporter,
                        ManagerType.PRIMARY,
                        PageRequest.of(0, 1)
                )
                .stream()
                .findFirst()
                .orElse(null);
    }
}