
package com.example.senioron.domain.inquiry.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 문의 답변 등록 요청")
public record AdminInquiryAnswerRequest(

        @Schema(
                description = "답변 내용 (1~2000자, 공백만 입력 불가)",
                example = "문의하신 내용을 확인했습니다.",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String content,

        @Schema(
                description = "문의 처리 상태 (WAITING 또는 COMPLETED)",
                example = "COMPLETED",
                allowableValues = {"WAITING", "COMPLETED"},
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String status
) {
}
