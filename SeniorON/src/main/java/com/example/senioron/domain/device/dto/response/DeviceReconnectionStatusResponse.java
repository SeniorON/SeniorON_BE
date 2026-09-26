package com.example.senioron.domain.device.dto.response;

public record DeviceReconnectionStatusResponse(
        boolean familyConnected,
        boolean deviceDisconnected
) {
}