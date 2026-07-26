package com.example.senioron.domain.device.dto.response;

import com.example.senioron.domain.device.entity.DeviceStatus;

import java.time.LocalDateTime;

public record DeviceDetailResponse(

        String deviceName,

        boolean connected,

        DeviceStatus connectionStatus,

        Integer batteryLevel,

        boolean networkConnected,

        LocalDateTime lastConnectedAt,

        LocalDateTime lastLocationUpdatedAt
) {

    public static DeviceDetailResponse disconnected() {

        return new DeviceDetailResponse(
                null,
                false,
                null,
                null,
                false,
                null,
                null
        );
    }
}