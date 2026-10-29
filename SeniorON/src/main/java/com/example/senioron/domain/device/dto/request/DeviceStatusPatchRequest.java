
package com.example.senioron.domain.device.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record DeviceStatusPatchRequest(

        @NotBlank
        String deviceIdentifier,

        String deviceName,

        @Min(0)
        @Max(100)
        Integer batteryLevel,

        Boolean charging,

        Boolean deviceStatusSharingEnabled,

        Boolean networkConnected,

        Boolean defaultHomeEnabled,

        Boolean locationPermissionGranted,

        Boolean gpsEnabled,

        Boolean notificationPermissionGranted,

        Boolean appExecutionMaintained
) {
}
