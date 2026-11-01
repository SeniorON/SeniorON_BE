
package com.example.senioron.domain.inquiry.controller;

import com.example.senioron.domain.inquiry.dto.response.AdminInquiryListResponse;
import com.example.senioron.domain.inquiry.service.AdminInquiryService;
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
        name = "Admin - Inquiry",
        description = "관리자 문의 관리 API"
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/inquiries")
public class AdminInquiryController {

    private final AdminInquiryService adminInquiryService;

    @Operation(
            summary = "관리자 문의 목록 조회",
            description = "문의 상태와 제목 또는 작성자 이름으로 검색하고 최신순으로 페이지네이션하여 조회합니다."
    )
    @GetMapping
    public Response<AdminInquiryListResponse> getInquiries(
            @Parameter(
                    description = "문의 상태 (WAITING, COMPLETED). 생략 시 전체 조회"
            )
            @RequestParam(required = false)
            String status,

            @Parameter(
                    description = "문의 제목 또는 작성자 원본 이름 검색"
            )
            @RequestParam(required = false)
            String keyword,

            @Parameter(
                    description = "페이지 번호 (0부터 시작)"
            )
            @RequestParam(defaultValue = "0")
            int page,

            @Parameter(
                    description = "페이지 크기 (기본 20, 대시보드는 5)"
            )
            @RequestParam(defaultValue = "20")
            int size
    ) {
        return Response.ok(
                adminInquiryService.getInquiries(
                        status,
                        keyword,
                        page,
                        size
                )
        );
    }
}
