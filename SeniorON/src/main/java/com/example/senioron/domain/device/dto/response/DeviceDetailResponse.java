package com.example.senioron.domain.device.dto.response;

import com.example.senioron.domain.device.entity.DeviceStatus;

import java.time.LocalDateTime;

public record DeviceDetailResponse(
        String deviceName,
        boolean connected,
        DeviceStatus connectionStatus,
        Integer batteryLevel,
        Boolean charging,
        Boolean deviceStatusSharingEnabled,
        Boolean networkConnected,
        Boolean defaultHomeEnabled,
        Boolean locationPermissionGranted,
        Boolean gpsEnabled,
        Boolean notificationPermissionGranted,
        Boolean appExecutionMaintained,
        LocalDateTime lastConnectedAt,
        LocalDateTime lastLocationUpdatedAt
) {

    public static DeviceDetailResponse disconnected() {
        return new DeviceDetailResponse(
                null,
                false,
                DeviceStatus.DISCONNECTED,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}