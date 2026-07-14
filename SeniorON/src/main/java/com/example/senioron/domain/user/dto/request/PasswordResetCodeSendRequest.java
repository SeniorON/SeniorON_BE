package com.example.senioron.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PasswordResetCodeSendRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String loginId;
}
