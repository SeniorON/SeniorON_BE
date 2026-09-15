package com.example.senioron.domain.senior.controller;

import com.example.senioron.domain.senior.dto.request.SeniorReloginRequestCreateRequest;
import com.example.senioron.domain.senior.dto.response.SeniorReloginRequestCreateResponse;
import com.example.senioron.domain.senior.service.SeniorReloginRequestService;
import com.example.senioron.global.apiPayload.response.Response;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
}
