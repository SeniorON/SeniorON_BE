package com.example.senioron.global.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class EmailServiceTest {
    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final EmailService emailService = new EmailService(mailSender);

    @Test
    void adminPasswordResetMailHasSubjectLinkAndOneHourNotice() {
        String link = "https://admin.example.com/invitation?token=reset-token";

        emailService.sendAdminPasswordReset("admin@example.com", link);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertThat(message.getTo()).containsExactly("admin@example.com");
        assertThat(message.getSubject()).isEqualTo("[SeniorON] 관리자 비밀번호 재설정 안내");
        assertThat(message.getText()).contains(link).contains("1시간");
    }
}
