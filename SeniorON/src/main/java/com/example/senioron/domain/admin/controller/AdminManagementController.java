package com.example.senioron.domain.admin.controller;

import com.example.senioron.domain.admin.dto.request.AdminInviteRequest;
import com.example.senioron.domain.admin.dto.request.AdminUpdateRequest;
import com.example.senioron.domain.admin.dto.response.AdminInviteResponse;
import com.example.senioron.domain.admin.dto.response.AdminPageResponse;
import com.example.senioron.domain.admin.dto.response.AdminSummaryResponse;
import com.example.senioron.domain.admin.entity.Admin;
import com.example.senioron.domain.admin.service.AdminInvitationService;
import com.example.senioron.domain.admin.service.AdminManagementService;
import com.example.senioron.domain.user.service.ClientIpResolver;
import com.example.senioron.global.apiPayload.response.Response;
import com.example.senioron.global.apiPayload.code.ResultCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;

@RestController
@Tag(name = "관리자 계정", description = "운영자 초대, 목록 조회, 정보 수정, 비밀번호 재설정 API")
@RequestMapping("/api/admin/admins")
@RequiredArgsConstructor
public class AdminManagementController {
    private final AdminInvitationService invitationService;
    private final AdminManagementService managementService;
    private final ClientIpResolver clientIpResolver;

    @Operation(
            summary = "관리자 목록 조회",
            description = "이름·이메일로 관리자를 검색합니다. 최신 생성순으로 정렬하며 size는 최대 100입니다."
    )
    @GetMapping
    public Response<AdminPageResponse> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return Response.ok(managementService.search(keyword, page, size));
    }

    @Operation(
            summary = "관리자 정보 수정",
            description = "SUPER 관리자가 다른 관리자의 권한을 바꾸거나 계정을 비활성화·재활성화합니다."
    )
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PatchMapping("/{adminId}")
    public Response<AdminSummaryResponse> update(
            @AuthenticationPrincipal Admin admin,
            @PathVariable Long adminId,
            @RequestBody AdminUpdateRequest request,
            HttpServletRequest servletRequest
    ) {
        return Response.ok(managementService.update(
                admin, adminId, request, clientIpResolver.resolve(servletRequest)));
    }

    @Operation(summary = "관리자 초대", description = "SUPER 관리자가 새 관리자를 초대합니다.")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping("/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public Response<AdminInviteResponse> invite(
            @AuthenticationPrincipal Admin admin,
            @Valid @RequestBody AdminInviteRequest request,
            HttpServletRequest servletRequest
    ) {
        return Response.ok(ResultCode.CREATED, invitationService.invite(
                admin, request, clientIpResolver.resolve(servletRequest)));
    }

    @Operation(
            summary = "관리자 비밀번호 재설정 메일 발송",
            description = "SUPER 관리자가 활성 상태인 다른 관리자에게 비밀번호 재설정 링크(1시간 유효)를 메일로 보냅니다. "
                    + "이전에 발송한 재설정 링크는 무효화됩니다."
    )
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping("/{adminId}/password-reset")
    public Response<Void> sendPasswordReset(
            @AuthenticationPrincipal Admin admin,
            @PathVariable Long adminId,
            HttpServletRequest servletRequest
    ) {
        invitationService.sendPasswordReset(admin, adminId, clientIpResolver.resolve(servletRequest));
        return Response.ok();
    }
}
