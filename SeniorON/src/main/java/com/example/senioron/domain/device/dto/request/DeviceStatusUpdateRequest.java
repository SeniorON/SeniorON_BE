package com.example.senioron.domain.device.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DeviceStatusUpdateRequest(

        @NotBlank
        String deviceIdentifier,

        @NotBlank
        String deviceName,

        @NotNull
        @Min(0)
        @Max(100)
        Integer batteryLevel
) {
}