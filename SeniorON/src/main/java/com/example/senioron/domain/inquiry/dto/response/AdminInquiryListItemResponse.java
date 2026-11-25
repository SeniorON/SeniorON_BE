
package com.example.senioron.domain.inquiry.dto.response;

import com.example.senioron.domain.inquiry.entity.Inquiry;
import com.example.senioron.domain.inquiry.entity.InquiryStatus;
import com.example.senioron.domain.user.util.UserNameMasker;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminInquiryListItemResponse {

    private Long inquiryId;
    private String title;
    private String writerName;
    private InquiryStatus status;
    private LocalDateTime createdAt;

    public static AdminInquiryListItemResponse from(Inquiry inquiry) {
        return AdminInquiryListItemResponse.builder()
                .inquiryId(inquiry.getInquiryId())
                .title(inquiry.getTitle())
                .writerName(UserNameMasker.mask(inquiry.getUser()))
                .status(inquiry.getStatus())
                .createdAt(inquiry.getCreatedAt())
                .build();
    }
}
