package com.example.senioron.domain.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SignupEmailVerificationCodeSendRequest {

    @Email
    @NotBlank
    private String email;
}
