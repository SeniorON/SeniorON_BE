package com.example.senioron.domain.admin.controller;

import com.example.senioron.domain.admin.dto.request.AdminInviteRequest;
import com.example.senioron.domain.admin.dto.response.AdminInviteResponse;
import com.example.senioron.domain.admin.entity.Admin;
import com.example.senioron.domain.admin.service.AdminInvitationService;
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
@Tag(name = "Admin - Account")
@RequestMapping("/api/admin/admins")
@RequiredArgsConstructor
public class AdminManagementController {
    private final AdminInvitationService invitationService;
    private final ClientIpResolver clientIpResolver;

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
}
