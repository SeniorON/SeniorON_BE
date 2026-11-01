
package com.example.senioron.domain.inquiry.service;

import com.example.senioron.domain.inquiry.dto.response.AdminInquiryListResponse;
import com.example.senioron.domain.inquiry.entity.InquiryStatus;
import com.example.senioron.domain.inquiry.repository.InquiryRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminInquiryService {

    private final InquiryRepository inquiryRepository;

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
