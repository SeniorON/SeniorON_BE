package com.example.senioron.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PasswordResetRequest {

    @NotNull
    private Long verificationId;

    @NotBlank
    private String newPassword;

    @NotBlank
    private String newPasswordCheck;
}
