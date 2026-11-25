
package com.example.senioron.domain.inquiry.dto.response;

import com.example.senioron.domain.inquiry.entity.Inquiry;
import com.example.senioron.domain.inquiry.entity.InquiryStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "관리자 문의 처리 상태 변경 응답")
public record AdminInquiryStatusUpdateResponse(

        @Schema(description = "문의 ID", example = "1023")
        Long inquiryId,

        @Schema(description = "처리 상태", example = "WAITING")
        InquiryStatus status,

        @Schema(description = "완료 일시")
        LocalDateTime completedAt,

        @Schema(description = "완료 처리한 관리자 이름")
        String completedByName
) {

    public static AdminInquiryStatusUpdateResponse from(
            Inquiry inquiry
    ) {
        return new AdminInquiryStatusUpdateResponse(
                inquiry.getInquiryId(),
                inquiry.getStatus(),
                inquiry.getCompletedAt(),
                inquiry.getCompletedByAdmin() == null
                        ? null
                        : inquiry.getCompletedByAdmin().getName()
        );
    }
}
