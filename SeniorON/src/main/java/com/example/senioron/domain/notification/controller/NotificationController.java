package com.example.senioron.domain.notification.controller;

import com.example.senioron.domain.notification.dto.request.NotificationSettingRequest;
import com.example.senioron.domain.notification.dto.response.NotificationHomeListResponse;
import com.example.senioron.domain.notification.dto.response.NotificationListResponse;
import com.example.senioron.domain.notification.dto.response.NotificationSettingResponse;
import com.example.senioron.domain.notification.dto.response.ParentDeviceStatusResponse;
import com.example.senioron.domain.notification.entity.NotificationSettingType;
import com.example.senioron.domain.notification.entity.NotificationType;
import com.example.senioron.domain.notification.service.NotificationService;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import com.example.senioron.global.apiPayload.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "알림",description = "알림 관련 메소드")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notification")
@Validated
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "알림 설정 홈화면 조회", description = "SOS/무활동/위험사이트/외출·귀가 4가지 알림 설정의 현재 on-off 상태와 활성화된 개수를 조회합니다")
    @GetMapping("/setting")
    public Response<NotificationHomeListResponse> getNotificationHome(
            @AuthenticationPrincipal User user
    ){
        if (user.getRole() != Role.CHILD) {
            throw new BusinessException(ErrorCode.NOTIFICATION_CHILD_ONLY);
        }
        return Response.ok(notificationService.getHomeSettings(user.getUsersId()));
    }

    @Operation(summary = "부모님 기기 연결상태 조회", description = "같은 가족 부모님(PARENT) 기기가 온라인인지 조회합니다. 가족/부모/기기 정보가 없으면 false를 반환합니다.")
    @GetMapping("/parent-device-status")
    public Response<ParentDeviceStatusResponse> getParentDeviceStatus(
            @AuthenticationPrincipal User user
    ){
        if (user.getRole() != Role.CHILD) {
            throw new BusinessException(ErrorCode.NOTIFICATION_CHILD_ONLY);
        }
        return Response.ok(notificationService.getParentDeviceStatus(user.getUsersId()));
    }

    @Operation(summary = "알림 설정 토글 기능", description = "SOS 알림은 필수 알림이라 토글 대상에서 제외됩니다.")
    @PatchMapping("/setting/{type}")
    public Response<NotificationSettingResponse> updateSetting(
            @PathVariable NotificationSettingType type,
            @Valid @RequestBody NotificationSettingRequest req,
            @AuthenticationPrincipal User user
    ){
        if (user.getRole() != Role.CHILD) {
            throw new BusinessException(ErrorCode.NOTIFICATION_CHILD_ONLY);
        }
        return Response.ok(notificationService.updateSetting(user.getUsersId(), type, req.getEnabled()));
    }

    @Operation(summary = "알림 기록 조회", description = "SOS/무활동/위험사이트/외출·귀가 알림들의 내역을 조회합니다.")
    @GetMapping
    public Response<NotificationListResponse> getNotifications(
            @AuthenticationPrincipal User user,
            @RequestParam NotificationType type,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size

            ){
        return Response.ok(notificationService.getNotificationList(user.getUsersId(), type, cursor, size));
    }

    @Operation(summary = "알림 읽음 처리", description = "본인이 받은 알림만 읽음 처리할 수 있습니다.")
    @PatchMapping("/{notificationId}/read")
    public Response<Void> markAsRead(
            @AuthenticationPrincipal User user,
            @PathVariable Long notificationId
    ){
        notificationService.markAsRead(user.getUsersId(), notificationId);
        return Response.ok();
    }

    @Operation(summary = "알림 삭제", description = "본인이 받은 알림만 삭제할 수 있습니다.")
    @DeleteMapping("/{notificationId}")
    public Response<Void> deleteNotification(
            @AuthenticationPrincipal User user,
            @PathVariable Long notificationId
    ){
        notificationService.deleteNotification(user.getUsersId(), notificationId);
        return Response.ok();
    }
}
