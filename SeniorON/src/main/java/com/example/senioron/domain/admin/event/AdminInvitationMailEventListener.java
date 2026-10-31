package com.example.senioron.domain.admin.event;

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
        if (mailUsername == null || mailUsername.isBlank()) {
            if (logLink) {
                log.info("관리자 초대 링크: {}", event.link());
            } else {
                log.warn("메일 설정이 없어 관리자 초대 메일을 발송하지 못했습니다: {}",
                        maskEmail(event.email()));
            }
            return;
        }
        try {
            emailService.sendAdminInvitation(event.email(), event.link());
        } catch (RuntimeException e) {
            log.error("관리자 초대 메일 발송 실패: {}", maskEmail(event.email()), e);
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
