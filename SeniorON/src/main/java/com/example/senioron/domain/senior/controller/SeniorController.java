package com.example.senioron.domain.senior.controller;

import com.example.senioron.domain.senior.dto.request.SeniorCreateRequest;
import com.example.senioron.domain.senior.dto.response.ManagedSeniorResponse;
import com.example.senioron.domain.senior.dto.response.ParentSeniorProfileResponse;
import com.example.senioron.domain.senior.dto.response.SeniorCreateResponse;
import com.example.senioron.domain.senior.dto.response.SeniorFamilyResponse;
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

    @Operation(summary = "내가 속한 가족/시니어 목록", description = "현재 로그인한 사용자가 속한 가족과 각 가족에 연결된 시니어를 전환 목록으로 조회합니다.")
    @GetMapping("/me")
    public Response<List<ManagedSeniorResponse>> getManagedSeniors(
            @AuthenticationPrincipal User user
    ) {
        return Response.ok(
                seniorService.getManagedSeniors(user)
        );
    }

    @Operation(summary = "우리 가족 시니어 목록 조회", description = "지정한 가족에 속한 시니어 목록을 조회합니다.")
    @GetMapping("/family/{familyId}")
    public Response<List<SeniorFamilyResponse>> getFamilySeniors(
            @AuthenticationPrincipal User user,
            @PathVariable Long familyId
    ) {
        return Response.ok(
                seniorService.getFamilySeniors(user, familyId)
        );
    }

    @Operation(summary = "내 시니어 프로필 ID 조회", description = "현재 로그인한 부모 계정과 연결된 시니어 프로필의 ID를 조회합니다.")
    @GetMapping("/me/profile")
    public Response<ParentSeniorProfileResponse> getParentSeniorProfile(
            @AuthenticationPrincipal User user
    ) {
        return Response.ok(
                seniorService.getParentSeniorProfile(user)
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

}
