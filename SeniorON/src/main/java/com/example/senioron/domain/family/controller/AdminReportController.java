package com.example.senioron.domain.family.controller;

import com.example.senioron.domain.family.dto.response.AdminReportListResponse;
import com.example.senioron.domain.family.dto.response.AdminReportStatsResponse;
import com.example.senioron.domain.family.service.AdminReportService;
import com.example.senioron.global.apiPayload.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "관리자 신고",
        description = "관리자 신고 목록·통계 조회 API"
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

            @Parameter(description = "페이지 번호 (0부터 시작)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "페이지 크기 (기본 10)")
            @RequestParam(defaultValue = "10") int size
    ) {
        return Response.ok(
                adminReportService.getReports(type, keyword, page, size)
        );
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