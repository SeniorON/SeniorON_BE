
package com.example.senioron.domain.inquiry.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 문의 답변 등록 응답")
public record AdminInquiryAnswerResponse(

        @Schema(
                description = "등록된 답변 ID",
                example = "1"
        )
        Long answerId
) {
}
