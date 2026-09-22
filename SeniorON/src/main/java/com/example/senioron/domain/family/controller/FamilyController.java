package com.example.senioron.domain.family.controller;

import com.example.senioron.domain.family.dto.request.FamilyJoinRequest;
import com.example.senioron.domain.family.dto.request.FamilyPrimaryManagerUpdateRequest;
import com.example.senioron.domain.family.dto.request.PhotoGroupConnectRequest;
import com.example.senioron.domain.family.dto.response.*;
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


@Tag(name = "가족", description = "시니어 코드 등 가족 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/family")
public class FamilyController {

    private final FamilyService familyService;

    @Operation(summary = "시니어 코드 생성", description = "새로운 가족을 생성하고 시니어 코드를 발급합니다. 시니어 코드 생성은 자식(CHILD)만 가능합니다.")
    @PostMapping("code-create")
    public Response<SeniorCodeCreateResponse> createFamily(@AuthenticationPrincipal User user) {
        return Response.ok(familyService.createFamily(user));
    }

    @Operation(summary = "시니어 코드로 참여", description = "시니어 코드를 입력해 기존 가족에 참여합니다.")
    @PostMapping("/join")
    public Response<FamilyJoinResponse> joinFamily(@AuthenticationPrincipal User user, @Valid @RequestBody FamilyJoinRequest request) {
        return Response.ok(familyService.joinFamily(user, request));
    }

    @Operation(summary = "가족 구성원 조회", description = "선택한 시니어의 가족 구성원 목록을 조회합니다.")
    @GetMapping("/members")
    public Response<List<FamilyMemberResponse>> getFamilyMembers(
            @AuthenticationPrincipal User user,
            @RequestParam Long seniorId
    ){
        return Response.ok(familyService.getFamilyMembers(user, seniorId));
    }

    @Operation(summary = "주 담당자 변경", description = "선택한 시니어 가족의 주 담당자를 같은 가족의 다른 자녀 구성원으로 변경합니다.")
    @PatchMapping("/primary-manager")
    public Response<FamilyPrimaryManagerUpdateResponse> updatePrimaryManager(
            @AuthenticationPrincipal User user,
            @RequestParam Long seniorId,
            @Valid @RequestBody FamilyPrimaryManagerUpdateRequest request
    ) {
        return Response.ok(familyService.updatePrimaryManager(user, seniorId, request));
    }

    @Operation(summary = "가족 구성원 삭제", description = "주 담당자가 선택한 시니어 가족의 구성원을 가족에서 제외합니다.")
    @DeleteMapping("/members/{targetUserId}")
    public Response<Void> removeFamilyMember(
            @AuthenticationPrincipal User user,
            @RequestParam Long seniorId,
            @PathVariable Long targetUserId
    ) {
        familyService.removeFamilyMember(user, seniorId, targetUserId);
        return Response.ok();
    }

    @Operation(summary = "가족 메인 화면 조회", description = "선택한 시니어의 가족 구성원과 공유 그룹의 최근 사진 및 업로더 프로필을 조회합니다.")
    @GetMapping("/home")
    public Response<FamilyHomeResponse> getFamilyHome(
            @AuthenticationPrincipal User user,
            @RequestParam Long seniorId
    ){
        return Response.ok(familyService.getFamilyHome(user, seniorId));
    }

    @Operation(summary = "시니어 코드 조회", description = "선택한 시니어 가족의 가입 코드와 구성원 수를 조회합니다.")
    @GetMapping("/code")
    public Response<SeniorCodeResponse> getSeniorCode(
            @AuthenticationPrincipal User user,
            @RequestParam Long seniorId
    ) {
        return Response.ok(
                familyService.getSeniorCode(user, seniorId)
        );
    }

    @Operation(summary = "사진 공유 시니어 연결", description = "선택한 시니어의 가족과 입력한 시니어 코드의 가족을 새로운 사진 공유 그룹으로 연결합니다.")
    @PostMapping("/photo-groups/connections")
    public Response<Void> connectPhotoGroup(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody PhotoGroupConnectRequest request
    ) {
        familyService.connectPhotoGroup(user, request);
        return Response.ok();
    }

    @Operation(summary = "사진 공유 시니어 연결 해제", description = "현재 선택한 시니어 가족의 주 담당자가 연결된 사진 공유 관계를 해제합니다.")
    @DeleteMapping("/photo-groups/connections/{photoGroupId}")
    public Response<Void> disconnectPhotoGroup(
            @AuthenticationPrincipal User user,
            @RequestParam Long seniorId,
            @PathVariable Long photoGroupId
    ) {
        familyService.disconnectPhotoGroup(user, seniorId, photoGroupId);

        return Response.ok();
    }

    @Operation(summary = "연결된 시니어 조회", description = "선택한 시니어 가족과 사진 공유 그룹으로 직접 연결된 시니어 목록을 조회합니다.")
    @GetMapping("/photo-groups/connections")
    public Response<List<ConnectedSeniorResponse>> getConnectedSeniors(
            @AuthenticationPrincipal User user,
            @RequestParam Long seniorId
    ) {
        return Response.ok(
                familyService.getConnectedSeniors(user, seniorId)
        );
    }

}
