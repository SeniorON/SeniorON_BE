
package com.example.senioron.domain.inquiry.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "관리자 문의 답변 수정 응답")
public record AdminInquiryAnswerUpdateResponse(

        @Schema(description = "수정된 답변 ID", example = "5")
        Long answerId,

        @Schema(description = "답변 수정 일시")
        LocalDateTime updatedAt
) {
}
