package com.example.senioron.domain.family.dto.response;

import com.example.senioron.domain.family.entity.AdminReportType;
import com.example.senioron.domain.family.entity.PhotoReportReason;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "관리자 신고 목록 항목")
public record AdminReportListItemResponse(

        @Schema(description = "1부터 시작하는 전체 접수 순번", example = "41")
        long number,

        @Schema(description = "신고 유형 (상세 이동에 사용)", example = "PHOTO")
        AdminReportType reportType,

        @Schema(description = "유형별 신고 ID (상세 이동에 사용)", example = "12")
        Long reportId,

        @Schema(description = "게시자 이름", example = "김지은")
        String targetName,

        @Schema(description = "사진 업로드일시. 게시자 신고이거나 사진이 삭제됐으면 null", nullable = true)
        LocalDateTime photoUploadedAt,

        @Schema(description = "신고 사유 코드", example = "ADVERTISEMENT")
        PhotoReportReason reason,

        @Schema(description = "마스킹된 신고자 이름", example = "박*훈")
        String reporterName,

        @Schema(description = "접수일시")
        LocalDateTime createdAt,

        @Schema(description = "누적 신고 횟수", example = "3")
        long cumulativeCount,

        @Schema(description = "누적 횟수 강조 여부", example = "true")
        boolean highlighted
) {
}