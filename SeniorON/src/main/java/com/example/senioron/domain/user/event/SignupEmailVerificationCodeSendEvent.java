package com.example.senioron.domain.user.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class SignupEmailVerificationCodeSendEvent {

    private final String email;
    private final String verificationCode;
}
