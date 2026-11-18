package com.example.senioron.domain.socialaccount.dto.google.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class GoogleLoginRequest {

    @NotBlank(message = "Firebase ID 토큰은 필수입니다.")
    private String firebaseIdToken;

    private String fcmToken;

    private String deviceIdentifier;
}
