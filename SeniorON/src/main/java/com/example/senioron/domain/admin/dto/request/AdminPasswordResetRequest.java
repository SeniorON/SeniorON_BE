package com.example.senioron.domain.admin.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AdminPasswordResetRequest(@NotBlank @Email String email) { }
