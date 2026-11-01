
package com.example.senioron.domain.inquiry.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 문의 답변 수정 요청")
public record AdminInquiryAnswerUpdateRequest(

        @Schema(
                description = "수정할 답변 내용 (1~2000자)",
                example = "수정된 답변 내용",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String content
) {
}
