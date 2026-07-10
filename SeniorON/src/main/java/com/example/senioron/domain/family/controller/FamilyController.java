package com.example.senioron.domain.family.controller;

import com.example.senioron.domain.family.dto.request.FamilyJoinRequest;
import com.example.senioron.domain.family.dto.response.FamilyCodeCreateResponse;
import com.example.senioron.domain.family.dto.response.FamilyJoinResponse;
import com.example.senioron.domain.family.service.FamilyService;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.global.apiPayload.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


@Tag(name = "가족", description = "가족코드 등 외의 가족 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/family")
public class FamilyController {

    private final FamilyService familyService;

    @Operation(summary = "가족 공유코드 생성", description = "새로운 가족을 생성하고 가족 공유코드를 발급합니다. 가족코드 생성은 자식(CHILD)만 가능합니다.")
    @PostMapping("code-create")
    public Response<FamilyCodeCreateResponse> createFamily(@AuthenticationPrincipal User user) {
        return Response.ok(familyService.createFamily(user));
    }

    @Operation(summary = "가족 공유코드로 참여", description = "가족 공유코드를 입력해 기존 가족에 참여합니다.")
    @PostMapping("/join")
    public Response<FamilyJoinResponse> joinFamily(@AuthenticationPrincipal User user, @Valid @RequestBody FamilyJoinRequest request) {
        return Response.ok(familyService.joinFamily(user, request));
    }
}