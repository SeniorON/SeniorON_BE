package com.example.senioron.domain.admin.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doThrow;

import com.example.senioron.domain.admin.entity.AdminInvitationType;
import com.example.senioron.global.mail.EmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {
        "spring.mail.username=test@senioron.site",
        "admin.invitation.log-link=false"
})
class AdminInvitationMailEventIntegrationTest {
    @Autowired private ApplicationEventPublisher publisher;
    @Autowired private TransactionTemplate transactionTemplate;
    @MockitoBean private EmailService emailService;

    @Test
    void rollbackDoesNotSendInvitationMailButCommitSendsOnce() {
        transactionTemplate.executeWithoutResult(status -> {
            publisher.publishEvent(new AdminInvitationMailEvent(
                    "rollback@example.com", "https://example.com/invitation?token=rollback-secret"));
            status.setRollbackOnly();
        });
        verify(emailService, never()).sendAdminInvitation(anyString(), anyString());

        transactionTemplate.executeWithoutResult(status -> publisher.publishEvent(
                new AdminInvitationMailEvent(
                        "commit@example.com", "https://example.com/invitation?token=commit-secret")));
        verify(emailService, times(1)).sendAdminInvitation(anyString(), anyString());
    }

    @Test
    void disabledLinkLoggingDoesNotExposeTokenOrInvitationUrl(CapturedOutput output) {
        AdminInvitationMailEventListener listener =
                new AdminInvitationMailEventListener(emailService);
        ReflectionTestUtils.setField(listener, "mailUsername", "");
        ReflectionTestUtils.setField(listener, "logLink", false);
        listener.handle(new AdminInvitationMailEvent(
                "hidden@example.com", "https://example.com/invitation?token=raw-secret-token"));
        assertThat(output.getOut()).doesNotContain("raw-secret-token");
        assertThat(output.getOut()).doesNotContain("/invitation?token=");
        assertThat(output.getOut()).doesNotContain("hidden@example.com");
        assertThat(output.getOut()).contains("h***@example.com");
    }

    @Test
    void mailFailureLogMasksRecipientEmail(CapturedOutput output) {
        doThrow(new RuntimeException("mail failure"))
                .when(emailService).sendAdminInvitation(anyString(), anyString());
        AdminInvitationMailEventListener listener =
                new AdminInvitationMailEventListener(emailService);
        ReflectionTestUtils.setField(listener, "mailUsername", "sender@example.com");
        ReflectionTestUtils.setField(listener, "logLink", false);
        listener.handle(new AdminInvitationMailEvent(
                "alice@example.com", "https://example.com/invitation?token=secret"));
        assertThat(output.getOut()).doesNotContain("alice@example.com");
        assertThat(output.getOut()).contains("a***@example.com");
    }

    @Test
    void rollbackDoesNotSendPasswordResetMailButCommitSendsResetMailOnce() {
        transactionTemplate.executeWithoutResult(status -> {
            publisher.publishEvent(new AdminInvitationMailEvent("rollback-reset@example.com",
                    "https://example.com/invitation?token=rollback-reset", AdminInvitationType.RESET));
            status.setRollbackOnly();
        });
        verify(emailService, never()).sendAdminPasswordReset(anyString(), anyString());

        transactionTemplate.executeWithoutResult(status -> publisher.publishEvent(
                new AdminInvitationMailEvent("commit-reset@example.com",
                        "https://example.com/invitation?token=commit-reset", AdminInvitationType.RESET)));
        verify(emailService, times(1)).sendAdminPasswordReset(anyString(), anyString());
        verify(emailService, never()).sendAdminInvitation(anyString(), anyString());
    }

    @Test
    void disabledLinkLoggingHidesResetTokenAndMasksEmail(CapturedOutput output) {
        AdminInvitationMailEventListener listener =
                new AdminInvitationMailEventListener(emailService);
        ReflectionTestUtils.setField(listener, "mailUsername", "");
        ReflectionTestUtils.setField(listener, "logLink", false);
        listener.handle(new AdminInvitationMailEvent("reset@example.com",
                "https://example.com/invitation?token=raw-reset-token", AdminInvitationType.RESET));
        assertThat(output.getOut()).doesNotContain("raw-reset-token");
        assertThat(output.getOut()).doesNotContain("reset@example.com");
        assertThat(output.getOut()).contains("r***@example.com");
        verify(emailService, never()).sendAdminPasswordReset(anyString(), anyString());
    }

    @Test
    void enabledLinkLoggingPrintsResetLinkWithoutSending(CapturedOutput output) {
        AdminInvitationMailEventListener listener =
                new AdminInvitationMailEventListener(emailService);
        ReflectionTestUtils.setField(listener, "mailUsername", "");
        ReflectionTestUtils.setField(listener, "logLink", true);
        listener.handle(new AdminInvitationMailEvent("reset@example.com",
                "https://example.com/invitation?token=logged-reset-token", AdminInvitationType.RESET));
        assertThat(output.getOut()).contains("관리자 비밀번호 재설정 링크: https://example.com/invitation?token=logged-reset-token");
        verify(emailService, never()).sendAdminPasswordReset(anyString(), anyString());
    }

    @Test
    void resetMailFailureLogMasksRecipientEmail(CapturedOutput output) {
        doThrow(new RuntimeException("mail failure"))
                .when(emailService).sendAdminPasswordReset(anyString(), anyString());
        AdminInvitationMailEventListener listener =
                new AdminInvitationMailEventListener(emailService);
        ReflectionTestUtils.setField(listener, "mailUsername", "sender@example.com");
        ReflectionTestUtils.setField(listener, "logLink", false);
        listener.handle(new AdminInvitationMailEvent("bob@example.com",
                "https://example.com/invitation?token=secret", AdminInvitationType.RESET));
        assertThat(output.getOut()).doesNotContain("bob@example.com");
        assertThat(output.getOut()).contains("b***@example.com");
    }
}
