package com.example.senioron.domain.family.service;

import com.example.senioron.domain.family.dto.request.FamilyUserReportRequest;
import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.entity.FamilyReportOutcome;
import com.example.senioron.domain.family.entity.FamilyUserReport;
import com.example.senioron.domain.family.entity.PhotoReportStatus;
import com.example.senioron.domain.family.repository.FamilyPhotoRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoSeniorRepository;
import com.example.senioron.domain.family.repository.FamilyUserReportRepository;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FamilyUserReportService {

    private final FamilyPhotoRepository familyPhotoRepository;
    private final FamilyUserReportRepository familyUserReportRepository;
    private final FamilyPhotoSeniorRepository familyPhotoSeniorRepository;
    private final UserRepository userRepository;

    @Transactional
    public FamilyReportOutcome reportUploader(
            User principal,
            Long familyPhotoId,
            FamilyUserReportRequest request
    ) {
        // 같은 신고자의 최초 저장과 기존 신고 조회가 겹치지 않도록 한다.
        User currentUser = userRepository
                .findByIdForUpdate(principal.getUsersId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 숨김 필터 없이 조회한 뒤, 이미 숨긴 게시자인지 별도로 확인한다.
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

        FamilyUserReport existingReport = familyUserReportRepository
                .findByReportedUser_UsersIdAndReporter_UsersId(uploader.getUsersId(), currentUser.getUsersId())
                .orElse(null);
        if (existingReport != null && existingReport.isHidden()) {
            throw new BusinessException(ErrorCode.FAMILY_USER_ALREADY_REPORTED);
        }

        Senior contextSenior = resolveContextSenior(photo, currentUser, request.getSeniorId());

        if (existingReport != null) {
            if (request.getHide()) {
                existingReport.hide();
                return FamilyReportOutcome.ALREADY_REPORTED_AND_HIDDEN;
            }
            return FamilyReportOutcome.ALREADY_REPORTED;
        }

        FamilyUserReport report = FamilyUserReport.builder()
                .reporter(currentUser)
                .reportedUser(uploader)
                .familyPhoto(photo)
                .senior(contextSenior)
                .imageKey(photo.getImageKey())
                .reason(request.getReason())
                .detail(request.getDetail())
                .hidden(request.getHide())
                .status(PhotoReportStatus.PENDING)
                .build();

        familyUserReportRepository.saveAndFlush(report);
        return FamilyReportOutcome.CREATED;
    }

    // 사용자는 여러 가족에 속할 수 있어, 관리자 상세의 시니어 코드·역할 기준이 될 시니어를 정한다.
    // 기준은 게시물 신고(FamilyPhotoReportService)와 같다.
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
