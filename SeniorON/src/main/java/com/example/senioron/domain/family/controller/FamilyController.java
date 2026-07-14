package com.example.senioron.domain.family.controller;

import com.example.senioron.domain.family.dto.request.FamilyJoinRequest;
import com.example.senioron.domain.family.dto.request.FamilyPrimaryManagerUpdateRequest;
import com.example.senioron.domain.family.dto.response.FamilyCodeCreateResponse;
import com.example.senioron.domain.family.dto.response.FamilyJoinResponse;
import com.example.senioron.domain.family.dto.response.FamilyMemberResponse;
import com.example.senioron.domain.family.dto.response.FamilyPrimaryManagerUpdateResponse;
import com.example.senioron.domain.family.service.FamilyService;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.global.apiPayload.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;


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

    @Operation(summary = "가족 구성원 조회", description = "현재 로그인한 사용자가 속한 가족의 구성원 목록을 조회합니다.")
    @GetMapping("/members")
    public Response<List<FamilyMemberResponse>> getFamilyMembers(
            @AuthenticationPrincipal User user
    ){
        return Response.ok(familyService.getFamilyMembers(user));
    }

    @Operation(summary = "주 담당자 변경", description = "현재 주 담당자를 같은 가족의 다른 구성원으로 변경합니다.")
    @PatchMapping("/primary-manager")
    public Response<FamilyPrimaryManagerUpdateResponse> updatePrimaryManager(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody FamilyPrimaryManagerUpdateRequest request
    ){
        return Response.ok(familyService.updatePrimaryManager(user, request));
    }

    @Operation(summary = "가족 구성원 삭제", description = "주 담당자가 같은 가족의 구성원을 가족에서 제외합니다.")
    @DeleteMapping("/members/{targetUserId}")
    public Response<Void> removeFamilyMember(
            @AuthenticationPrincipal User user,
            @PathVariable Long targetUserId
    ) {
        familyService.removeFamilyMember(user, targetUserId);
        return Response.ok();
    }

}