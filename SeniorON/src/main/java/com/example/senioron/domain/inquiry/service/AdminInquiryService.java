
package com.example.senioron.domain.inquiry.service;

import com.example.senioron.domain.inquiry.dto.request.AdminInquiryAnswerRequest;
import com.example.senioron.domain.inquiry.dto.response.AdminInquiryAnswerResponse;
import com.example.senioron.domain.inquiry.dto.response.AdminInquiryDetailResponse;
import com.example.senioron.domain.inquiry.dto.response.AdminInquiryListResponse;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminInquiryService {

    private static final int MAX_ANSWER_LENGTH = 2000;

    private final InquiryRepository inquiryRepository;
    private final InquiryAnswerRepository inquiryAnswerRepository;
    private final S3Service s3Service;

    // 관리자 문의 목록 조회 (#600)
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

    // 관리자 문의 상세 조회 (#602)
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

    // 관리자 문의 답변 등록 (#605)
    @Transactional
    public AdminInquiryAnswerResponse createAnswer(
            Long inquiryId,
            AdminInquiryAnswerRequest request
    ) {
        validateAnswerRequest(request);

        InquiryStatus inquiryStatus =
                parseRequiredStatus(request.status());

        Inquiry inquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(
                        () -> new BusinessException(
                                ErrorCode.INQUIRY_NOT_FOUND
                        )
                );

        InquiryAnswer answer = InquiryAnswer.builder()
                .inquiry(inquiry)
                .content(request.content())
                .build();

        InquiryAnswer savedAnswer =
                inquiryAnswerRepository.saveAndFlush(answer);

        inquiry.updateStatus(inquiryStatus);

        return new AdminInquiryAnswerResponse(
                savedAnswer.getInquiryAnswerId()
        );
    }

    private void validateAnswerRequest(
            AdminInquiryAnswerRequest request
    ) {
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        String content = request.content();

        if (content == null
                || content.isBlank()
                || content.length() > MAX_ANSWER_LENGTH) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
    }

    private InquiryStatus parseRequiredStatus(
            String status
    ) {
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
