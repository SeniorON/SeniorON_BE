package com.example.senioron.domain.user.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SignupEmailVerificationCodeVerifyResponse {

    private Boolean verified;
}
