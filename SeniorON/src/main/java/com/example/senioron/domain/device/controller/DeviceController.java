package com.example.senioron.domain.device.controller;

import com.example.senioron.domain.device.dto.request.DeviceStatusUpdateRequest;
import com.example.senioron.domain.device.service.DeviceService;
import com.example.senioron.domain.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService deviceService;

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
}