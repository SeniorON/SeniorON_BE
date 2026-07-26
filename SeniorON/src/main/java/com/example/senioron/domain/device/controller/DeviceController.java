package com.example.senioron.domain.device.controller;

import com.example.senioron.domain.device.dto.request.DeviceStatusUpdateRequest;
import com.example.senioron.domain.device.service.DeviceService;
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