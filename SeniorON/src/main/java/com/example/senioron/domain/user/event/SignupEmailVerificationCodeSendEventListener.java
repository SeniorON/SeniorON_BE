package com.example.senioron.domain.user.event;

import com.example.senioron.global.mail.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class SignupEmailVerificationCodeSendEventListener {

    private final EmailService emailService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(SignupEmailVerificationCodeSendEvent event) {
        emailService.sendSignupVerificationCode(
                event.getEmail(),
                event.getVerificationCode()
        );
    }
}
