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
                log.warn("메일 설정이 없어 관리자 초대 메일을 발송하지 못했습니다: {}", event.email());
            }
            return;
        }
        try {
            emailService.sendAdminInvitation(event.email(), event.link());
        } catch (RuntimeException e) {
            log.error("관리자 초대 메일 발송 실패: {}", event.email(), e);
        }
    }
}
