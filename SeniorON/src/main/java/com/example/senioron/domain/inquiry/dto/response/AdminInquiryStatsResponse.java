
package com.example.senioron.domain.inquiry.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "관리자 문의 통계 조회 응답")
public record AdminInquiryStatsResponse(

        @Schema(description = "답변 대기 중 문의 건수", example = "7")
        long waitingCount,

        @Schema(description = "오늘 접수된 문의 건수", example = "4")
        long todayCount,

        @Schema(description = "최근 7일 접수 문의의 평균 최초 응답시간 (시간)", example = "3.2", nullable = true)
        Double avgResponseHours,

        @Schema(description = "이번 주 해결된 문의 건수", example = "21")
        long weekCompletedCount
) {
}
