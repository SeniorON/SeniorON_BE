package com.example.senioron.domain.admin.controller;

import com.example.senioron.domain.admin.dto.request.AdminInviteRequest;
import com.example.senioron.domain.admin.dto.response.AdminInviteResponse;
import com.example.senioron.domain.admin.entity.Admin;
import com.example.senioron.domain.admin.service.AdminInvitationService;
import com.example.senioron.domain.user.service.ClientIpResolver;
import com.example.senioron.global.apiPayload.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Admin - Account")
@RequestMapping("/api/admin/admins")
@RequiredArgsConstructor
public class AdminManagementController {
    private final AdminInvitationService invitationService;
    private final ClientIpResolver clientIpResolver;

    @Operation(summary = "관리자 초대", description = "SUPER 관리자가 새 관리자를 초대합니다.")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping("/invitations")
    public Response<AdminInviteResponse> invite(
            @AuthenticationPrincipal Admin admin,
            @Valid @RequestBody AdminInviteRequest request,
            HttpServletRequest servletRequest
    ) {
        return Response.ok(invitationService.invite(
                admin, request, clientIpResolver.resolve(servletRequest)));
    }

    @Operation(summary = "관리자 초대 재발송", description = "INVITED 관리자의 초대 링크를 재발급합니다.")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping("/{adminId}/invitations/resend")
    public Response<Void> resend(
            @AuthenticationPrincipal Admin admin,
            @PathVariable Long adminId,
            HttpServletRequest servletRequest
    ) {
        invitationService.resend(admin, adminId, clientIpResolver.resolve(servletRequest));
        return Response.ok();
    }
}
