package com.example.senioron.domain.admin.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AdminPasswordChangeRequest(
        @NotBlank String currentPassword,
        @NotBlank String newPassword
) { }
