package com.example.senioron.domain.family.controller;

import com.example.senioron.domain.family.dto.response.AdminPhotoReportDetailResponse;
import com.example.senioron.domain.family.dto.response.AdminReportListResponse;
import com.example.senioron.domain.family.dto.response.AdminReportStatsResponse;
import com.example.senioron.domain.family.dto.response.AdminUserReportDetailResponse;
import com.example.senioron.domain.family.dto.response.AdminUserReportPhotoListResponse;
import com.example.senioron.domain.family.service.AdminReportService;
import com.example.senioron.global.apiPayload.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "관리자 신고",
        description = "관리자 신고 목록·상세·통계 조회 API"
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/reports")
public class AdminReportController {

    private final AdminReportService adminReportService;

    @Operation(
            summary = "관리자 신고 목록 조회",
            description = "게시물 신고와 게시자 신고를 합쳐 최신 접수순으로 조회합니다."
    )
    @GetMapping
    public Response<AdminReportListResponse> getReports(
            @Parameter(description = "신고 유형 (PHOTO, USER). 생략 시 전체 조회")
            @RequestParam(required = false) String type,

            @Parameter(description = "게시자 또는 신고자 원본 이름 검색")
            @RequestParam(required = false) String keyword,

            @Parameter(description = "게시자 사용자 ID. 해당 게시자가 받은 신고만 조회 (게시자 신고 내역 보기)")
            @RequestParam(required = false) Long reportedUserId,

            @Parameter(description = "페이지 번호 (0부터 시작)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "페이지 크기 (기본 10)")
            @RequestParam(defaultValue = "10") int size
    ) {
        return Response.ok(
                adminReportService.getReports(type, keyword, reportedUserId, page, size)
        );
    }

    @Operation(
            summary = "관리자 게시물 신고 상세 조회",
            description = "신고된 사진, 신고 정보, 누적 신고 횟수를 조회합니다. 같은 사진의 신고도 신고마다 따로 조회합니다."
    )
    @GetMapping("/photos/{reportId}")
    public Response<AdminPhotoReportDetailResponse> getPhotoReportDetail(
            @Parameter(description = "게시물 신고 ID (목록의 reportId)")
            @PathVariable Long reportId
    ) {
        return Response.ok(adminReportService.getPhotoReportDetail(reportId));
    }

    @Operation(
            summary = "관리자 게시자 신고 상세 조회",
            description = "신고된 게시자(역할·시니어 코드·누적 신고·업로드 사진 수·가입일), 신고 근거 사진(원본 URL·삭제 여부), 최근 업로드 사진 5장, "
                    + "신고 정보, 게시자가 받은 신고 이력(게시물 신고 + 게시자 신고, 최신순)을 조회합니다."
    )
    @GetMapping("/users/{reportId}")
    public Response<AdminUserReportDetailResponse> getUserReportDetail(
            @Parameter(description = "게시자 신고 ID (목록의 reportId)")
            @PathVariable Long reportId
    ) {
        return Response.ok(adminReportService.getUserReportDetail(reportId));
    }

    @Operation(
            summary = "관리자 게시자 업로드 사진 목록 조회",
            description = "게시자 신고의 게시자가 올린 전체 사진을 최신순으로 페이지당 18장씩 조회합니다. "
                    + "사진 더 보기 화면과 게시자 신고 사진 뷰어(이전/다음, 하단 썸네일)에 사용합니다."
    )
    @GetMapping("/users/{reportId}/photos")
    public Response<AdminUserReportPhotoListResponse> getUserReportPhotos(
            @Parameter(description = "게시자 신고 ID (목록의 reportId)")
            @PathVariable Long reportId,

            @Parameter(description = "페이지 번호 (0부터 시작, 페이지당 18장)")
            @RequestParam(defaultValue = "0") int page
    ) {
        return Response.ok(adminReportService.getUserReportPhotos(reportId, page));
    }

    @Operation(
            summary = "관리자 신고 통계 조회",
            description = "전체 신고, 오늘 접수, 3회 이상 신고된 게시자, 이번 주 접수를 조회합니다."
    )
    @GetMapping("/stats")
    public Response<AdminReportStatsResponse> getStats() {
        return Response.ok(adminReportService.getStats());
    }
}
