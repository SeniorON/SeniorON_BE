package com.example.senioron.domain.family.dto.response;

import com.example.senioron.domain.family.entity.AdminReportType;
import com.example.senioron.domain.family.entity.PhotoReportReason;
import com.example.senioron.domain.user.entity.ManagerType;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "관리자 게시자 신고 상세 조회 응답")
public record AdminUserReportDetailResponse(

        @Schema(description = "목록과 같은 전체 접수 순번 (제목·신고 번호에 표시)", example = "2040")
        long number,

        @Schema(description = "신고 유형", example = "USER")
        AdminReportType reportType,

        @Schema(description = "신고된 게시자")
        ReportedUser reportedUser,

        @Schema(description = "게시자가 최근 올린 사진 (최신순, 최대 5장)")
        List<RecentPhoto> recentPhotos,

        @Schema(description = "신고 정보")
        Report report,

        @Schema(description = "게시자가 받은 신고 이력 (게시물 신고 + 게시자 신고, 최신순)")
        List<History> histories
) {

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Schema(description = "신고된 게시자")
    public record ReportedUser(

            @Schema(description = "게시자 사용자 ID (게시자 사진 목록·신고 내역 보기에 사용)", example = "20")
            Long userId,

            @Schema(description = "게시자 이름", example = "김지은")
            String name,

            @Schema(description = "게시자 역할 (PRIMARY: 주담당자, SUB: 보조담당자). 확인할 수 없으면 null", example = "PRIMARY", nullable = true)
            ManagerType managerType,

            @Schema(description = "시니어 코드. 확인할 수 없으면 null", example = "A3F9-K2M7", nullable = true)
            String seniorCode,

            @Schema(description = "누적 신고 횟수 (게시물 신고 + 게시자 신고)", example = "3")
            long reportCount,

            @Schema(description = "누적 신고 강조 여부 (3회 이상)", example = "true")
            boolean highlighted,

            @Schema(description = "업로드 사진 수", example = "18")
            long photoCount,

            @Schema(description = "가입일시")
            LocalDateTime joinedAt
    ) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Schema(description = "최근 업로드 사진")
    public record RecentPhoto(

            @Schema(description = "사진 ID (사진 보기에 사용)", example = "40")
            Long familyPhotoId,

            @Schema(description = "썸네일 URL. 썸네일이 없으면 원본 URL")
            String thumbnailUrl
    ) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Schema(description = "신고 정보")
    public record Report(

            @Schema(description = "게시자 신고 ID", example = "7")
            Long reportId,

            @Schema(description = "신고 사유 코드", example = "ABUSE")
            PhotoReportReason reason,

            @Schema(description = "상세 내용", example = "불쾌한 문구가 담긴 사진을 계속 올려요.", nullable = true)
            String detail,

            @Schema(description = "마스킹된 신고자 이름", example = "이*진")
            String reporterName,

            @Schema(description = "신고자 역할 (PRIMARY: 주담당자, SUB: 보조담당자). 확인할 수 없으면 null", example = "PRIMARY", nullable = true)
            ManagerType reporterManagerType,

            @Schema(description = "접수일시")
            LocalDateTime createdAt,

            @Schema(description = "신고자 설정: 이 게시자의 게시물 보지 않기 여부", example = "true")
            boolean hidden
    ) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Schema(description = "신고 이력")
    public record History(

            @Schema(description = "신고 유형 (PHOTO: 게시물 신고, USER: 게시자 신고)", example = "USER")
            AdminReportType reportType,

            @Schema(description = "신고 ID (유형별 상세 조회에 사용)", example = "7")
            Long reportId,

            @Schema(description = "신고 사유 코드", example = "ABUSE")
            PhotoReportReason reason,

            @Schema(description = "접수일시")
            LocalDateTime createdAt,

            @Schema(description = "마스킹된 신고자 이름", example = "이*진")
            String reporterName
    ) {
    }
}
