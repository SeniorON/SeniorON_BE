package com.example.senioron.domain.admin.event;

import com.example.senioron.domain.admin.entity.AdminInvitationType;
import com.example.senioron.global.mail.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInvitationMailEventListener {
    private final EmailService emailService;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${admin.invitation.log-link:false}")
    private boolean logLink;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(AdminInvitationMailEvent event) {
        boolean reset = event.type() == AdminInvitationType.RESET;
        String label = reset ? "관리자 비밀번호 재설정" : "관리자 초대";
        if (mailUsername == null || mailUsername.isBlank()) {
            if (logLink) {
                log.info("{} 링크: {}", label, event.link());
            } else {
                log.warn("메일 설정이 없어 {} 메일을 발송하지 못했습니다: {}",
                        label, maskEmail(event.email()));
            }
            return;
        }
        try {
            if (reset) {
                emailService.sendAdminPasswordReset(event.email(), event.link());
            } else {
                emailService.sendAdminInvitation(event.email(), event.link());
            }
        } catch (RuntimeException e) {
            log.error("{} 메일 발송 실패: {}", label, maskEmail(event.email()), e);
        }
    }

    private String maskEmail(String email) {
        int separator = email == null ? -1 : email.indexOf('@');
        if (separator <= 0 || separator == email.length() - 1) {
            return "***";
        }
        String local = email.substring(0, separator);
        String domain = email.substring(separator + 1);
        String maskedLocal = local.length() == 1 ? "***" : local.charAt(0) + "***";
        return maskedLocal + "@" + domain;
    }
}
