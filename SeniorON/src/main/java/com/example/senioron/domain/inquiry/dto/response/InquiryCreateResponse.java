package com.example.senioron.domain.inquiry.dto.response;

import com.example.senioron.domain.inquiry.entity.Inquiry;
import com.example.senioron.domain.inquiry.entity.InquiryStatus;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InquiryCreateResponse {

    private Long inquiryId;
    private String title;
    private InquiryStatus status;
    private LocalDateTime createdAt;
    private List<String> imageUrls;

    public static InquiryCreateResponse from(
            Inquiry inquiry,
            List<String> imageUrls
    ) {
        return InquiryCreateResponse.builder()
                .inquiryId(inquiry.getInquiryId())
                .title(inquiry.getTitle())
                .status(inquiry.getStatus())
                .createdAt(inquiry.getCreatedAt())
                .imageUrls(imageUrls)
                .build();
    }
}
