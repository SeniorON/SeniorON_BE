package com.example.senioron.domain.inquiry.controller;

import com.example.senioron.domain.inquiry.dto.request.InquiryCreateRequest;
import com.example.senioron.domain.inquiry.dto.response.InquiryCreateResponse;
import com.example.senioron.domain.inquiry.service.InquiryService;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.global.apiPayload.code.ResultCode;
import com.example.senioron.global.apiPayload.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "1:1 문의", description = "1:1 문의 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/inquiries")
public class InquiryController {

    private final InquiryService inquiryService;

    @Operation(summary = "1:1 문의 등록", description = "현재 로그인한 사용자가 1:1 문의글을 등록합니다.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Response<InquiryCreateResponse> createInquiry(
            @Parameter(hidden = true)
            @AuthenticationPrincipal User user,
            @RequestPart(name = "request", required = false) InquiryCreateRequest request,
            @RequestPart(name = "images", required = false) List<MultipartFile> images
    ) {
        InquiryCreateResponse result = inquiryService.createInquiry(
                user,
                request,
                images
        );

        return Response.ok(ResultCode.CREATED, result);
    }
}
