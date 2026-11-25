package com.example.senioron.domain.device.dto.request;

import jakarta.validation.constraints.NotBlank;

public record FcmTokenUpdateRequest(

        @NotBlank
        String deviceIdentifier,

        @NotBlank
        String deviceToken
) {
}
