package com.example.senioron.domain.notification.controller;

import com.example.senioron.domain.notification.dto.request.NotificationSettingRequest;
import com.example.senioron.domain.notification.dto.response.NotificationHomeResponse;
import com.example.senioron.domain.notification.dto.response.NotificationSettingResponse;
import com.example.senioron.domain.notification.entity.NotificationType;
import com.example.senioron.domain.notification.service.NotificationService;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import com.example.senioron.global.apiPayload.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "알림",description = "알림 관련 메소드")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notification")
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "알림 설정 홈화면 조회", description = "SOS/무활동/위험사이트/외출·귀가 4가지 알림 설정의 현재 on-off 상태를 조회합니다")
    @GetMapping("/setting")
    public Response<List<NotificationHomeResponse>> getNotificationHome(
            @AuthenticationPrincipal User user
    ){
        if (user.getRole() != Role.CHILD) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return Response.ok(notificationService.getHomeSettings(user.getUsersId()));
    }

    @Operation(summary = "알림 설정 토글 기능")
    @PatchMapping("/setting/{type}")
    public Response<NotificationSettingResponse> updateSetting(
            @PathVariable NotificationType type,
            @RequestBody NotificationSettingRequest req,
            @AuthenticationPrincipal User user
    ){
        if (user.getRole() != Role.CHILD) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return Response.ok(notificationService.updateSetting(user.getUsersId(), type, req.getEnabled()));
    }
}
