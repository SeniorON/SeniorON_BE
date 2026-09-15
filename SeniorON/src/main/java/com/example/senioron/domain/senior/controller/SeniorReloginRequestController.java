package com.example.senioron.domain.senior.controller;

import com.example.senioron.domain.senior.dto.request.SeniorReloginRequestCreateRequest;
import com.example.senioron.domain.senior.dto.response.SeniorReloginRequestApproveResponse;
import com.example.senioron.domain.senior.dto.response.SeniorReloginRequestCreateResponse;
import com.example.senioron.domain.senior.service.SeniorReloginRequestService;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.global.apiPayload.response.Response;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/seniors/relogin-requests")
public class SeniorReloginRequestController {

    private final SeniorReloginRequestService seniorReloginRequestService;

    @PostMapping
    public Response<SeniorReloginRequestCreateResponse> create(
            @Valid @RequestBody SeniorReloginRequestCreateRequest request
    ) {
        return Response.ok(seniorReloginRequestService.create(request));
    }

    @PatchMapping("/{requestId}/approve")
    public Response<SeniorReloginRequestApproveResponse> approve(
            @AuthenticationPrincipal User user,
            @PathVariable Long requestId
    ) {
        return Response.ok(seniorReloginRequestService.approve(requestId, user));
    }
}
