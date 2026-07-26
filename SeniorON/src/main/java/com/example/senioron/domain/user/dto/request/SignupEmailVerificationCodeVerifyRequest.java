package com.example.senioron.domain.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SignupEmailVerificationCodeVerifyRequest {

    @Email
    @NotBlank
    private String email;

    @Size(min = 6, max = 6)
    @NotBlank
    private String verificationCode;
}
