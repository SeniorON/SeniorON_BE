package com.example.senioron.domain.family.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "관리자 신고 목록 조회 응답")
public record AdminReportListResponse(
        AdminReportCountsResponse counts,
        List<AdminReportListItemResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}