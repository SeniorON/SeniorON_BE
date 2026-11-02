
package com.example.senioron.domain.inquiry.controller;

import com.example.senioron.domain.inquiry.dto.request.AdminInquiryAnswerRequest;
import com.example.senioron.domain.inquiry.dto.request.AdminInquiryAnswerUpdateRequest;
import com.example.senioron.domain.inquiry.dto.request.AdminInquiryStatusUpdateRequest;
import com.example.senioron.domain.inquiry.dto.response.AdminInquiryAnswerResponse;
import com.example.senioron.domain.inquiry.dto.response.AdminInquiryAnswerUpdateResponse;
import com.example.senioron.domain.inquiry.dto.response.AdminInquiryDetailResponse;
import com.example.senioron.domain.inquiry.dto.response.AdminInquiryListResponse;
import com.example.senioron.domain.inquiry.dto.response.AdminInquiryStatusUpdateResponse;
import com.example.senioron.domain.inquiry.service.AdminInquiryService;
import com.example.senioron.global.apiPayload.code.ResultCode;
import com.example.senioron.global.apiPayload.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(
        name = "관리자 문의",
        description = "관리자 문의 목록·통계·상세 조회, 답변 등록·수정, 처리 상태 변경 API"
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

    @Operation(
            summary = "관리자 문의 상세 조회",
            description = "문의 ID를 기준으로 문의 내용, 작성자, 첨부 이미지, 기기 정보 및 답변 내역을 조회합니다."
    )
    @GetMapping("/{inquiryId}")
    public Response<AdminInquiryDetailResponse> getInquiryDetail(
            @Parameter(
                    description = "조회할 문의 ID",
                    required = true
            )
            @PathVariable("inquiryId")
            Long inquiryId
    ) {
        return Response.ok(
                adminInquiryService.getInquiryDetail(inquiryId)
        );
    }

    @Operation(
            summary = "관리자 문의 답변 등록",
            description = "문의에 답변을 등록하고 요청한 처리 상태로 변경합니다."
    )
    @PostMapping("/{inquiryId}/answers")
    @ResponseStatus(HttpStatus.CREATED)
    public Response<AdminInquiryAnswerResponse> createAnswer(
            @Parameter(
                    description = "답변을 등록할 문의 ID",
                    required = true
            )
            @PathVariable("inquiryId")
            Long inquiryId,

            @RequestBody
            AdminInquiryAnswerRequest request,

            Authentication authentication
    ) {
        return Response.ok(
                ResultCode.CREATED,
                adminInquiryService.createAnswer(
                        inquiryId,
                        request,
                        authentication
                )
        );
    }

    @Operation(
            summary = "관리자 문의 답변 수정",
            description = "기존 문의 답변을 수정하고 관리자 활동 기록을 저장합니다."
    )
    @PatchMapping("/{inquiryId}/answers/{answerId}")
    public Response<AdminInquiryAnswerUpdateResponse> updateAnswer(
            @Parameter(
                    description = "문의 ID",
                    required = true
            )
            @PathVariable("inquiryId")
            Long inquiryId,

            @Parameter(
                    description = "답변 ID",
                    required = true
            )
            @PathVariable("answerId")
            Long answerId,

            @RequestBody
            AdminInquiryAnswerUpdateRequest request,

            Authentication authentication,

            HttpServletRequest httpServletRequest
    ) {
        return Response.ok(
                adminInquiryService.updateAnswer(
                        inquiryId,
                        answerId,
                        request,
                        authentication,
                        httpServletRequest.getRemoteAddr()
                )
        );
    }

    @Operation(
            summary = "관리자 문의 처리 상태 변경",
            description = "문의 처리 상태를 변경하고 실제 변경 시 관리자 활동 기록을 저장합니다."
    )
    @PatchMapping("/{inquiryId}/status")
    public Response<AdminInquiryStatusUpdateResponse> updateInquiryStatus(
            @Parameter(
                    description = "상태를 변경할 문의 ID",
                    required = true
            )
            @PathVariable("inquiryId")
            Long inquiryId,

            @RequestBody
            AdminInquiryStatusUpdateRequest request,

            Authentication authentication,

            HttpServletRequest httpServletRequest
    ) {
        return Response.ok(
                adminInquiryService.updateInquiryStatus(
                        inquiryId,
                        request,
                        authentication,
                        httpServletRequest.getRemoteAddr()
                )
        );
    }
}
