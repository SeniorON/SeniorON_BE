package com.example.senioron.domain.device.dto.response;

import com.example.senioron.domain.device.entity.Device;

import java.time.LocalDateTime;

public record DeviceLocationResponse(
        Double latitude,
        Double longitude,
        LocalDateTime lastLocationUpdatedAt
) {

    public static DeviceLocationResponse from(Device device) {
        return new DeviceLocationResponse(
                device.getLatitude(),
                device.getLongitude(),
                device.getLastLocationUpdatedAt()
        );
    }
}