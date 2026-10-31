package com.example.senioron.domain.admin.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AdminInvitationAcceptRequest(
        @NotBlank String token,
        @NotBlank String password
) { }
