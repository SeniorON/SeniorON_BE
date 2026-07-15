package com.example.senioron.domain.user.controller;

import com.example.senioron.domain.user.dto.request.NameUpdateRequest;
import com.example.senioron.domain.user.dto.response.CurrentNameResponse;
import com.example.senioron.domain.user.dto.response.NameUpdateResponse;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.service.UserSettingsService;
import com.example.senioron.global.apiPayload.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "회원 설정", description = "로그인 사용자의 계정 설정 관련 API")
@RestController
@RequestMapping("/api/users/settings")
@RequiredArgsConstructor
public class UserSettingsController {

    private final UserSettingsService userSettingsService;

    @Operation(summary = "현재 이름 조회", description = "로그인한 사용자의 현재 이름을 조회합니다.")
    @GetMapping("/name")
    public Response<CurrentNameResponse> getCurrentName(
            @AuthenticationPrincipal User user
    ) {
        return Response.ok(userSettingsService.getCurrentName(user));
    }

    @Operation(summary = "이름 변경", description = "로그인한 사용자의 이름을 새로운 이름으로 변경합니다.")
    @PatchMapping("/name")
    public Response<NameUpdateResponse> updateName(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody NameUpdateRequest request
    ) {
        return Response.ok(userSettingsService.updateName(user, request));
    }
}
