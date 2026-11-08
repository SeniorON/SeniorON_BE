package com.example.senioron.domain.family.dto.response;

import com.example.senioron.domain.family.entity.AdminReportType;
import com.example.senioron.domain.family.entity.PhotoReportReason;
import com.example.senioron.domain.user.entity.ManagerType;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "관리자 게시물 신고 상세 조회 응답")
public record AdminPhotoReportDetailResponse(

        @Schema(description = "목록과 같은 전체 접수 순번 (제목·신고 번호에 표시)", example = "2041")
        long number,

        @Schema(description = "신고 유형", example = "PHOTO")
        AdminReportType reportType,

        @Schema(description = "신고된 게시물")
        Photo photo,

        @Schema(description = "신고 정보")
        Report report,

        @Schema(description = "누적 신고")
        Cumulative cumulative
) {

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Schema(description = "신고된 게시물")
    public record Photo(

            @Schema(description = "원본 사진 URL (미리보기·원본 크게 보기)")
            String imageUrl,

            @Schema(description = "게시자가 사진을 삭제했는지 여부", example = "false")
            boolean deleted,

            @Schema(description = "게시자 사용자 ID (게시자 신고 내역 보기에 사용). 탈퇴로 확인할 수 없으면 null", example = "20", nullable = true)
            Long reportedUserId,

            @Schema(description = "게시자 이름", example = "김지은")
            String uploaderName,

            @Schema(description = "게시자 역할 (PRIMARY: 주담당자, SUB: 보조담당자). 확인할 수 없으면 null", example = "PRIMARY", nullable = true)
            ManagerType uploaderManagerType,

            @Schema(description = "사진 업로드일시. 확인할 수 없으면 null", nullable = true)
            LocalDateTime uploadedAt,

            @Schema(description = "시니어 코드. 확인할 수 없으면 null", example = "A3F9-K2M7", nullable = true)
            String seniorCode
    ) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Schema(description = "신고 정보")
    public record Report(

            @Schema(description = "게시물 신고 ID", example = "12")
            Long reportId,

            @Schema(description = "신고 사유 코드", example = "ADVERTISEMENT")
            PhotoReportReason reason,

            @Schema(description = "상세 내용", example = "광고성 사진이 계속 올라와요.", nullable = true)
            String detail,

            @Schema(description = "마스킹된 신고자 이름", example = "박*훈")
            String reporterName,

            @Schema(description = "신고자 역할 (PRIMARY: 주담당자, SUB: 보조담당자). 확인할 수 없으면 null", example = "SUB", nullable = true)
            ManagerType reporterManagerType,

            @Schema(description = "접수일시")
            LocalDateTime createdAt,

            @Schema(description = "신고자 설정: 이 사진 다시 보지 않기 여부", example = "true")
            boolean hidden
    ) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Schema(description = "누적 신고")
    public record Cumulative(

            @Schema(description = "이 게시물(같은 사진) 신고 횟수", example = "1")
            long photoReportCount,

            @Schema(description = "게시자 누적 신고 횟수 (게시물 신고 + 게시자 신고)", example = "3")
            long reportedUserReportCount,

            @Schema(description = "게시자 누적 신고 강조 여부 (3회 이상)", example = "true")
            boolean highlighted
    ) {
    }
}
