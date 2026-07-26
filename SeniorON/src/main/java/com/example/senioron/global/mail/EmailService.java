package com.example.senioron.global.mail;

import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender javaMailSender;

    public void sendPasswordResetVerificationCode(String to, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("[SeniorON] 비밀번호 재설정 인증번호");
        message.setText("비밀번호 재설정 인증번호는 " + code + " 입니다. 5분 이내에 입력해 주세요.");

        try {
            javaMailSender.send(message);
        } catch (MailException e) {
            throw new BusinessException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }

    public void sendSignupVerificationCode(String to, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("[SeniorON] 회원가입 이메일 인증 코드 안내");
        message.setText("회원가입을 위한 이메일 인증 코드는 다음과 같습니다.\n"
                + "인증 코드: " + code + "\n"
                + "해당 코드는 5분 후 만료됩니다.");

        try {
            javaMailSender.send(message);
        } catch (MailException e) {
            throw new BusinessException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }
}
