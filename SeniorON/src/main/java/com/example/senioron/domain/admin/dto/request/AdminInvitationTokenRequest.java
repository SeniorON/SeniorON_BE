package com.example.senioron.domain.admin.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AdminInvitationTokenRequest(@NotBlank String token) { }
