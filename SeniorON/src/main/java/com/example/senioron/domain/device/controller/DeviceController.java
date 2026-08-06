package com.example.senioron.domain.device.controller;

import com.example.senioron.domain.device.dto.request.DeviceStatusUpdateRequest;
import com.example.senioron.domain.device.dto.request.FcmTokenUpdateRequest;
import com.example.senioron.domain.device.service.DeviceService;
import com.example.senioron.domain.device.dto.request.DeviceLocationUpdateRequest;
import com.example.senioron.domain.device.dto.response.DeviceLocationResponse;
import com.example.senioron.domain.device.dto.response.HomeLocationResponse;
import com.example.senioron.domain.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(
        name = "기기",
        description = "시니어 기기 정보 관련 API"
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService deviceService;

    @Operation(
            summary = "시니어 기기 정보 갱신",
            description = "시니어 기기의 연결 상태와 배터리 정보를 갱신"
    )
    @PutMapping("/status")
    public ResponseEntity<Void> updateDeviceStatus(
            @Valid @RequestBody DeviceStatusUpdateRequest request,
            Authentication authentication
    ) {
        User currentUser =
                (User) authentication.getPrincipal();

        deviceService.updateDeviceStatus(
                currentUser,
                request
        );

        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "FCM 토큰 갱신",
            description = "로그인 상태를 유지한 채, 현재 기기(deviceIdentifier)의 FCM 토큰만 갱신"
    )
    @PatchMapping("/fcm-token")
    public ResponseEntity<Void> updateFcmToken(
            @Valid @RequestBody FcmTokenUpdateRequest request,
            Authentication authentication
    ) {
        User currentUser =
                (User) authentication.getPrincipal();

        deviceService.registerToken(
                currentUser,
                request.deviceToken(),
                request.deviceIdentifier()
        );

        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "시니어 최근 위치 조회",
            description = "자녀가 같은 가족에 연결된 시니어의 최근 위치를 조회"
    )
    @GetMapping("/location")
    public ResponseEntity<DeviceLocationResponse> getLatestLocation(
            Authentication authentication
    ) {
        User currentUser =
                (User) authentication.getPrincipal();

        return ResponseEntity.ok(
                deviceService.getLatestLocation(currentUser)
        );
    }
    @Operation(
            summary = "시니어 집 좌표 조회",
            description = "시니어가 자녀가 등록한 집 좌표를 조회"
    )
    @GetMapping("/home-location")
    public ResponseEntity<HomeLocationResponse> getHomeLocation(
            Authentication authentication
    ) {
        User currentUser =
                (User) authentication.getPrincipal();

        return ResponseEntity.ok(
                deviceService.getHomeLocation(currentUser)
        );
    }
    @Operation(
            summary = "시니어 현재 위치 갱신",
            description = "시니어 기기의 현재 위도와 경도를 갱신"
    )
    @PatchMapping("/location")
    public ResponseEntity<Void> updateLocation(
            @Valid @RequestBody DeviceLocationUpdateRequest request,
            Authentication authentication
    ) {
        User currentUser =
                (User) authentication.getPrincipal();

        deviceService.updateLocation(
                currentUser,
                request
        );

        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "시니어 기기 연결 해제",
            description = "주담당자 또는 보조담당자가 같은 가족의 시니어 기기 연결을 해제"
    )

    @DeleteMapping("/connection")
    public ResponseEntity<Void> disconnectDevice(
            Authentication authentication
    ) {
        User currentUser =
                (User) authentication.getPrincipal();

        deviceService.disconnectDevice(currentUser);

        return ResponseEntity.noContent().build();
    }
}