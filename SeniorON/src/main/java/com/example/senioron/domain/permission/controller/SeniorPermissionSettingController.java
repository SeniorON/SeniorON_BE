package com.example.senioron.domain.permission.controller;

import com.example.senioron.domain.permission.dto.response.SeniorPermissionSettingResponse;
import com.example.senioron.domain.permission.service.SeniorPermissionSettingService;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.global.apiPayload.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "시니어 권한 설정", description = "시니어 런처 기능 권한 설정 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/seniors/{seniorId}/permission-settings")
public class SeniorPermissionSettingController {

    private final SeniorPermissionSettingService seniorPermissionSettingService;

    @Operation(
            summary = "시니어 권한 설정 조회",
            description = "선택한 시니어의 위치 정보, 무응답 감지 기능 사용 여부를 조회합니다."
    )
    @GetMapping
    public Response<SeniorPermissionSettingResponse> getSetting(
            @AuthenticationPrincipal User user,
            @PathVariable Long seniorId
    ) {
        return Response.ok(seniorPermissionSettingService.getSetting(user, seniorId));
    }
}
