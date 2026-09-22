package com.example.senioron.domain.notification.service;

import com.example.senioron.domain.notification.dto.response.NotificationHomeUpdate;
import com.example.senioron.domain.notification.entity.NotificationType;
import com.example.senioron.domain.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationHomeWebSocketServiceTest {
    @Test
    void brokerFailureDoesNotEscapeCommitCallbackOrStopNextReceiver() {
        var messaging = mock(SimpMessagingTemplate.class);
        var service = new NotificationHomeWebSocketService(mock(ApplicationEventPublisher.class),
                messaging, mock(NotificationRepository.class));
        var payload = new NotificationHomeUpdate("NOTIFICATION_HOME_UPDATED", 100L, 10L,
                20L, NotificationType.SOS, "CREATED");
        doThrow(new IllegalStateException("broker unavailable"))
                .when(messaging).convertAndSendToUser(eq("1"), anyString(), any(Object.class));

        assertThatCode(() -> service.sendAfterCommit(new NotificationHomeWebSocketService.Delivery(1L, payload)))
                .doesNotThrowAnyException();
        service.sendAfterCommit(new NotificationHomeWebSocketService.Delivery(2L, payload));
        verify(messaging).convertAndSendToUser("2", NotificationHomeWebSocketService.DESTINATION, payload);
    }
}
