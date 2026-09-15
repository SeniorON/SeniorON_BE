package com.example.senioron.domain.senior.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SeniorReloginRequestCreateRequest(
        @NotBlank
        String deviceIdentifier,

        @NotBlank
        String deviceAuthToken
) {
}
