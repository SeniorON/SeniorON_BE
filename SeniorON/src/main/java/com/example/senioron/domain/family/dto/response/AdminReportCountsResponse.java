package com.example.senioron.domain.family.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "유형 탭 건수 (검색어 반영)")
public record AdminReportCountsResponse(
        long total,
        long photo,
        long user
) {
}