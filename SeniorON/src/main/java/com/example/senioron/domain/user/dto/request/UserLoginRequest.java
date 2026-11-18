package com.example.senioron.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserLoginRequest {

    @NotBlank
    private String loginId;

    @NotBlank
    private String password;

    private String fcmToken;

    private String deviceIdentifier;
}
