
package com.example.senioron.domain.inquiry.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 문의 처리 상태 변경 요청")
public record AdminInquiryStatusUpdateRequest(

        @Schema(
                description = "변경할 문의 처리 상태",
                example = "WAITING",
                allowableValues = {"WAITING", "COMPLETED"},
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String status
) {
}
