package com.example.senioron.domain.admin.controller;

import com.example.senioron.domain.admin.dto.request.AdminLoginRequest;
import com.example.senioron.domain.admin.dto.request.AdminPasswordChangeRequest;
import com.example.senioron.domain.admin.dto.request.AdminPasswordResetRequest;
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
import com.example.senioron.domain.user.service.ClientIpResolver;
import com.example.senioron.global.apiPayload.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자 인증", description = "관리자 로그인, 토큰, 초대 링크 확인·비밀번호 설정, 비밀번호 변경·찾기 API")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService service;
    private final AdminInvitationService invitationService;
    private final ClientIpResolver clientIpResolver;

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

    @Operation(
            summary = "관리자 비밀번호 변경",
            description = "현재 비밀번호를 확인한 뒤 새 비밀번호(영문·숫자 포함 8~64자)로 변경합니다. "
                    + "변경 시 해당 관리자의 모든 Refresh Token이 무효화됩니다."
    )
    @PatchMapping("/me/password")
    public Response<Void> changePassword(
            @AuthenticationPrincipal Admin admin,
            @Valid @RequestBody AdminPasswordChangeRequest request,
            HttpServletRequest servletRequest
    ) {
        service.changePassword(admin, request, clientIpResolver.resolve(servletRequest));
        return Response.ok();
    }

    @Operation(
            summary = "관리자 비밀번호 찾기",
            description = "입력한 이메일이 활성 관리자 계정이면 비밀번호 재설정 링크(1시간 유효)를 메일로 보냅니다. "
                    + "등록 여부·계정 상태와 관계없이 항상 같은 응답을 반환하며, 이메일·IP 기준 발송 횟수가 제한됩니다."
    )
    @PostMapping("/auth/password-reset")
    public Response<Void> requestPasswordReset(
            @Valid @RequestBody AdminPasswordResetRequest request,
            HttpServletRequest servletRequest
    ) {
        invitationService.requestPasswordReset(request, clientIpResolver.resolve(servletRequest));
        return Response.ok();
    }
}
