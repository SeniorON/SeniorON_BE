package com.example.senioron.domain.family.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 신고 통계 조회 응답")
public record AdminReportStatsResponse(

        @Schema(description = "전체 신고 수", example = "41")
        long totalCount,

        @Schema(description = "오늘 접수 수", example = "5")
        long todayCount,

        @Schema(description = "어제 같은 시점 대비 증감", example = "2")
        long todayDiff,

        @Schema(description = "게시자 신고를 3회 이상 받은 게시자 수", example = "4")
        long repeatedReportedUserCount,

        @Schema(description = "이번 주 접수 수 (월요일 시작)", example = "28")
        long weekCount,

        @Schema(description = "지난주 같은 시점 대비 증감", example = "4")
        long weekDiff
) {
}