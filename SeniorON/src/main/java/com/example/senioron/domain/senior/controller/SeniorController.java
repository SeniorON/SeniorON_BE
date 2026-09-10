package com.example.senioron.domain.senior.controller;

import com.example.senioron.domain.senior.dto.request.SeniorCreateRequest;
import com.example.senioron.domain.senior.dto.request.SeniorRelationUpdateRequest;
import com.example.senioron.domain.senior.dto.response.ManagedSeniorResponse;
import com.example.senioron.domain.senior.dto.response.SeniorCreateResponse;
import com.example.senioron.domain.senior.dto.response.SeniorRelationUpdateResponse;
import com.example.senioron.domain.senior.service.SeniorService;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.global.apiPayload.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "시니어", description = "부모님 및 시니어 정보 관련 API")
@RestController
@RequestMapping("/api/seniors")
@RequiredArgsConstructor
public class SeniorController {

    private final SeniorService seniorService;

    @Operation(summary = "관리 가능한 시니어 목록 조회", description = "현재 로그인한 사용자가 UserSenior 관계로 관리 가능한 시니어 목록을 조회합니다.")
    @GetMapping
    public Response<List<ManagedSeniorResponse>> getManagedSeniors(
            @AuthenticationPrincipal User user
    ) {
        return Response.ok(
                seniorService.getManagedSeniors(user)
        );
    }

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

    @Operation(summary = "시니어 관계 수정", description = "현재 로그인한 사용자와 시니어 사이의 관계만 생성하거나 수정합니다.")
    @PatchMapping("/{seniorId}/relation")
    public Response<SeniorRelationUpdateResponse> updateSeniorRelation(
            @AuthenticationPrincipal User user,
            @PathVariable Long seniorId,
            @Valid @RequestBody SeniorRelationUpdateRequest request
    ) {
        return Response.ok(
                seniorService.updateSeniorRelation(user, seniorId, request)
        );
    }
}
