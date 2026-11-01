
package com.example.senioron.domain.inquiry.dto.response;

import com.example.senioron.domain.inquiry.entity.Inquiry;
import com.example.senioron.domain.inquiry.entity.InquiryStatus;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.entity.UserStatus;
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
                .writerName(maskWriterName(inquiry.getUser()))
                .status(inquiry.getStatus())
                .createdAt(inquiry.getCreatedAt())
                .build();
    }

    private static String maskWriterName(User user) {
        if (user == null || user.getStatus() == UserStatus.WITHDRAWN) {
            return "탈퇴회원";
        }

        String name = user.getName();

        if (name == null || name.isBlank()) {
            return "익명";
        }

        int length = name.codePointCount(0, name.length());

        if (length == 1) {
            return "*";
        }

        String first = name.substring(
                0,
                name.offsetByCodePoints(0, 1)
        );

        if (length == 2) {
            return first + "*";
        }

        String last = name.substring(
                name.offsetByCodePoints(0, length - 1)
        );

        return first + "*" + last;
    }
}
