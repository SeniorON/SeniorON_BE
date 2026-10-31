package com.example.senioron.domain.admin.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doThrow;

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
}
