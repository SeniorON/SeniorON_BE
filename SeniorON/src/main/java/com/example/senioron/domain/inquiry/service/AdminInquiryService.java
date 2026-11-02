
package com.example.senioron.domain.inquiry.service;

import com.example.senioron.domain.admin.entity.Admin;
import com.example.senioron.domain.admin.entity.AdminActivityTargetType;
import com.example.senioron.domain.admin.entity.AdminActivityType;
import com.example.senioron.domain.admin.service.AdminActivityLogService;
import com.example.senioron.domain.inquiry.dto.request.AdminInquiryAnswerRequest;
import com.example.senioron.domain.inquiry.dto.request.AdminInquiryAnswerUpdateRequest;
import com.example.senioron.domain.inquiry.dto.request.AdminInquiryStatusUpdateRequest;
import com.example.senioron.domain.inquiry.dto.response.AdminInquiryAnswerResponse;
import com.example.senioron.domain.inquiry.dto.response.AdminInquiryAnswerUpdateResponse;
import com.example.senioron.domain.inquiry.dto.response.AdminInquiryDetailResponse;
import com.example.senioron.domain.inquiry.dto.response.AdminInquiryListResponse;
import com.example.senioron.domain.inquiry.dto.response.AdminInquiryStatusUpdateResponse;
import com.example.senioron.domain.inquiry.entity.Inquiry;
import com.example.senioron.domain.inquiry.entity.InquiryAnswer;
import com.example.senioron.domain.inquiry.entity.InquiryStatus;
import com.example.senioron.domain.inquiry.repository.InquiryAnswerRepository;
import com.example.senioron.domain.inquiry.repository.InquiryRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import com.example.senioron.global.storage.S3Service;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminInquiryService {

    private static final int MAX_ANSWER_LENGTH = 2000;

    private final InquiryRepository inquiryRepository;
    private final InquiryAnswerRepository inquiryAnswerRepository;
    private final AdminActivityLogService adminActivityLogService;
    private final S3Service s3Service;

    @Transactional(readOnly = true)
    public AdminInquiryListResponse getInquiries(
            String status,
            String keyword,
            int page,
            int size
    ) {
        validatePagination(page, size);

        InquiryStatus inquiryStatus = parseStatus(status);
        String normalizedKeyword = normalizeKeyword(keyword);

        return AdminInquiryListResponse.from(
                inquiryRepository.findAdminInquiries(
                        inquiryStatus,
                        normalizedKeyword,
                        PageRequest.of(page, size)
                )
        );
    }

    @Transactional(readOnly = true)
    public AdminInquiryDetailResponse getInquiryDetail(
            Long inquiryId
    ) {
        Inquiry inquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(
                        () -> new BusinessException(
                                ErrorCode.INQUIRY_NOT_FOUND
                        )
                );

        List<AdminInquiryDetailResponse.ImageResponse> images =
                inquiry.getImages()
                        .stream()
                        .map(image ->
                                AdminInquiryDetailResponse.ImageResponse.from(
                                        image,
                                        s3Service.getFileUrl(image.getImageUrl())
                                )
                        )
                        .toList();

        return AdminInquiryDetailResponse.from(
                inquiry,
                images
        );
    }

    @Transactional
    public AdminInquiryAnswerResponse createAnswer(
            Long inquiryId,
            AdminInquiryAnswerRequest request,
            Authentication authentication
    ) {
        validateAnswerRequest(request);

        InquiryStatus inquiryStatus =
                parseRequiredStatus(request.status());

        Inquiry inquiry = inquiryRepository
                .findByIdForUpdate(inquiryId)
                .orElseThrow(
                        () -> new BusinessException(
                                ErrorCode.INQUIRY_NOT_FOUND
                        )
                );

        if (inquiryAnswerRepository.existsByInquiry_InquiryId(inquiryId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        Admin admin = getCurrentAdmin(authentication);

        InquiryAnswer answer = InquiryAnswer.builder()
                .inquiry(inquiry)
                .createdByAdmin(admin)
                .content(request.content())
                .build();

        InquiryAnswer savedAnswer =
                inquiryAnswerRepository.saveAndFlush(answer);

        inquiry.changeStatus(inquiryStatus, admin);

        return new AdminInquiryAnswerResponse(
                savedAnswer.getInquiryAnswerId()
        );
    }

    @Transactional
    public AdminInquiryAnswerUpdateResponse updateAnswer(
            Long inquiryId,
            Long answerId,
            AdminInquiryAnswerUpdateRequest request,
            Authentication authentication,
            String ipAddress
    ) {
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        validateAnswerContent(request.content());

        InquiryAnswer answer = inquiryAnswerRepository
                .findByInquiryAnswerIdAndInquiry_InquiryId(
                        answerId,
                        inquiryId
                )
                .orElseThrow(
                        () -> new BusinessException(
                                ErrorCode.INQUIRY_ANSWER_NOT_FOUND
                        )
                );

        Admin admin = getCurrentAdmin(authentication);

        answer.updateContent(request.content());

        String title = answer.getInquiry().getTitle();

        if (title != null && title.length() > 100) {
            title = title.substring(0, 100);
        }

        adminActivityLogService.record(
                admin,
                AdminActivityType.INQUIRY_ANSWER_UPDATED,
                AdminActivityTargetType.INQUIRY,
                inquiryId,
                title,
                "문의 답변 수정 (답변 ID: " + answerId + ")",
                ipAddress
        );

        return new AdminInquiryAnswerUpdateResponse(
                answer.getInquiryAnswerId(),
                answer.getUpdatedAt()
        );
    }

    @Transactional
    public AdminInquiryStatusUpdateResponse updateInquiryStatus(
            Long inquiryId,
            AdminInquiryStatusUpdateRequest request,
            Authentication authentication,
            String ipAddress
    ) {
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        InquiryStatus newStatus =
                parseRequiredStatus(request.status());

        Inquiry inquiry = inquiryRepository
                .findByIdForUpdate(inquiryId)
                .orElseThrow(
                        () -> new BusinessException(
                                ErrorCode.INQUIRY_NOT_FOUND
                        )
                );

        Admin admin = getCurrentAdmin(authentication);

        InquiryStatus previousStatus = inquiry.getStatus();

        boolean changed = inquiry.changeStatus(
                newStatus,
                admin
        );

        if (changed) {
            String title = inquiry.getTitle();

            if (title != null && title.length() > 100) {
                title = title.substring(0, 100);
            }

            adminActivityLogService.record(
                    admin,
                    AdminActivityType.INQUIRY_STATUS_CHANGED,
                    AdminActivityTargetType.INQUIRY,
                    inquiryId,
                    title,
                    "문의 처리 상태 변경: "
                            + previousStatus
                            + " → "
                            + newStatus,
                    ipAddress
            );
        }

        return AdminInquiryStatusUpdateResponse.from(inquiry);
    }

    private Admin getCurrentAdmin(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Admin admin)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        if (!admin.isActive()) {
            throw new BusinessException(ErrorCode.ADMIN_NOT_ACTIVE);
        }

        return admin;
    }

    private void validateAnswerRequest(
            AdminInquiryAnswerRequest request
    ) {
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        validateAnswerContent(request.content());
    }

    private void validateAnswerContent(String content) {
        if (content == null
                || content.isBlank()
                || content.length() > MAX_ANSWER_LENGTH) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
    }

    private InquiryStatus parseRequiredStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        try {
            return InquiryStatus.valueOf(status);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
    }

    private void validatePagination(int page, int size) {
        if (page < 0 || size < 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
    }

    private InquiryStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }

        try {
            return InquiryStatus.valueOf(status.trim());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        return "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
    }
}
