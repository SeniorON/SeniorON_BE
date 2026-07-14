package com.example.senioron.domain.senior.controller;

import com.example.senioron.domain.senior.dto.request.SeniorCreateRequest;
import com.example.senioron.domain.senior.dto.response.SeniorCreateResponse;
import com.example.senioron.domain.senior.service.SeniorService;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.global.apiPayload.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "시니어", description = "부모님 및 시니어 정보 관련 API")
@RestController
@RequestMapping("/api/seniors")
@RequiredArgsConstructor
public class SeniorController {

    private final SeniorService seniorService;

    @Operation(summary = "시니어 정보 등록", description = "현재 로그인한 사용자가 관리할 시니어 정보를 등록합니다.")
    @PostMapping
    public Response<SeniorCreateResponse> createSenior(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody SeniorCreateRequest request
    ) {
        return Response.ok(
                seniorService.createSenior(user, request)
        );
    }
}