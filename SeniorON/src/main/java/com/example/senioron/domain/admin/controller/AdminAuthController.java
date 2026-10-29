package com.example.senioron.domain.admin.controller;

import com.example.senioron.domain.admin.dto.request.AdminLoginRequest;
import com.example.senioron.domain.admin.dto.request.AdminInvitationAcceptRequest;
import com.example.senioron.domain.admin.dto.request.AdminInvitationTokenRequest;
import com.example.senioron.domain.admin.dto.request.AdminRefreshRequest;
import com.example.senioron.domain.admin.dto.response.AdminLoginResponse;
import com.example.senioron.domain.admin.dto.response.AdminInvitationVerifyResponse;
import com.example.senioron.domain.admin.dto.response.AdminMeResponse;
import com.example.senioron.domain.admin.dto.response.AdminTokenResponse;
import com.example.senioron.domain.admin.entity.Admin;
import com.example.senioron.domain.admin.service.AdminAuthService;
import com.example.senioron.domain.admin.service.AdminInvitationService;
import com.example.senioron.global.apiPayload.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Auth", description = "관리자 인증 API")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService service;
    private final AdminInvitationService invitationService;

    @Operation(summary = "관리자 로그인", description = "이메일과 비밀번호로 관리자 토큰을 발급합니다.")
    @PostMapping("/auth/login")
    public Response<AdminLoginResponse> login(
            @Valid @RequestBody AdminLoginRequest request
    ) {
        return Response.ok(service.login(request));
    }

    @Operation(summary = "관리자 토큰 재발급", description = "Refresh Token을 회전하고 새 관리자 토큰을 발급합니다.")
    @PostMapping("/auth/token/refresh")
    public Response<AdminTokenResponse> refresh(
            @Valid @RequestBody AdminRefreshRequest request
    ) {
        return Response.ok(service.refresh(request));
    }

    @Operation(summary = "관리자 로그아웃", description = "전달된 관리자 Refresh Token을 폐기합니다.")
    @PostMapping("/auth/logout")
    public Response<Void> logout(
            @Valid @RequestBody AdminRefreshRequest request
    ) {
        service.logout(request.refreshToken());
        return Response.ok();
    }

    @Operation(summary = "관리자 정보 조회", description = "현재 인증된 관리자 정보를 조회합니다.")
    @GetMapping("/me")
    public Response<AdminMeResponse> me(@AuthenticationPrincipal Admin admin) {
        return Response.ok(service.me(admin));
    }

    @Operation(summary = "관리자 초대 확인", description = "초대 토큰의 유효성과 대상 이메일을 확인합니다.")
    @PostMapping("/auth/invitations/verify")
    public Response<AdminInvitationVerifyResponse> verifyInvitation(
            @Valid @RequestBody AdminInvitationTokenRequest request
    ) {
        return Response.ok(invitationService.verify(request));
    }

    @Operation(summary = "관리자 초대 수락", description = "초대받은 관리자의 비밀번호를 설정합니다.")
    @PostMapping("/auth/invitations/accept")
    public Response<Void> acceptInvitation(
            @Valid @RequestBody AdminInvitationAcceptRequest request
    ) {
        invitationService.accept(request);
        return Response.ok();
    }
}
