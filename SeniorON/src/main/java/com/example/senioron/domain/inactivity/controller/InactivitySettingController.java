package com.example.senioron.domain.inactivity.controller;

import com.example.senioron.domain.inactivity.dto.request.InactivitySettingRequest;
import com.example.senioron.domain.inactivity.dto.response.InactivitySettingResponse;
import com.example.senioron.domain.inactivity.service.InactivitySettingService;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.global.apiPayload.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "무활동 감지 설정", description = "가족 구성원의 무활동 감지 설정 관련 메소드")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/inactivity-settings")
public class InactivitySettingController {

    private final InactivitySettingService inactivitySettingService;

    @Operation(summary = "무활동 감지 설정 조회", description = "자녀가 같은 가족의 대상자(부모님)의 무활동 감지 설정을 조회합니다")
    @GetMapping("/{targetUserId}")
    public Response<InactivitySettingResponse> getSetting(
            @AuthenticationPrincipal User user,
            @PathVariable Long targetUserId
    ) {
        return Response.ok(inactivitySettingService.getSetting(user, targetUserId));
    }

    @Operation(summary = "무활동 감지 설정 수정", description = "자녀가 같은 가족의 대상자(부모님)의 무활동 감지 임계 시간을 수정합니다")
    @PatchMapping("/{targetUserId}")
    public Response<InactivitySettingResponse> updateSetting(
            @AuthenticationPrincipal User user,
            @PathVariable Long targetUserId,
            @Valid @RequestBody InactivitySettingRequest request
    ) {
        return Response.ok(inactivitySettingService.updateSetting(user, targetUserId, request));
    }
}
