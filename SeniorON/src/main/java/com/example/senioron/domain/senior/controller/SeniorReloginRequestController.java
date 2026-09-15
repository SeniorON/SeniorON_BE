package com.example.senioron.domain.senior.controller;

import com.example.senioron.domain.senior.dto.request.SeniorReloginRequestCreateRequest;
import com.example.senioron.domain.senior.dto.response.SeniorReloginRequestApproveResponse;
import com.example.senioron.domain.senior.dto.response.SeniorReloginRequestCreateResponse;
import com.example.senioron.domain.senior.dto.response.SeniorReloginRequestListResponse;
import com.example.senioron.domain.senior.service.SeniorReloginRequestService;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.global.apiPayload.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "재로그인", description = "재로그인 요청 및 승 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/seniors/relogin-requests")
public class SeniorReloginRequestController {

    private final SeniorReloginRequestService seniorReloginRequestService;

    @Operation(summary = "재로그인 요청", description = "시니어 기기의 로그인 토큰이 만료된 경우, 등록된 기기임을 인증하고 자녀에게 승인을 요청하기 위한 재로그인 요청을 생성합니다.")
    @PostMapping
    public Response<SeniorReloginRequestCreateResponse> create(
            @Valid @RequestBody SeniorReloginRequestCreateRequest request
    ) {
        return Response.ok(seniorReloginRequestService.create(request));
    }

    @Operation(summary = "재로그인 요청 승인", description = "자녀가 시니어 기기의 재로그인 요청을 승인합니다. 승인된 요청은 10분 동안 유효하며, 해당 시간 내에 시니어 기기의 로그인을 재활성화할 수 있습니다.")
    @PatchMapping("/{requestId}/approve")
    public Response<SeniorReloginRequestApproveResponse> approve(
            @AuthenticationPrincipal User user,
            @PathVariable Long requestId
    ) {
        return Response.ok(seniorReloginRequestService.approve(requestId, user));
    }

    @Operation(
            summary = "재로그인 요청 조회",
            description = "자녀가 자신이 관리하는 시니어의 현재 유효한 재로그인 요청을 조회합니다."
    )
    @GetMapping
    public Response<List<SeniorReloginRequestListResponse>> getPendingRequests(
            @AuthenticationPrincipal User user
    ) {
        return Response.ok(seniorReloginRequestService.getPendingRequests(user));
    }
}
