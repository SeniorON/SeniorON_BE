package com.example.senioron.domain.event.util;

import com.example.senioron.domain.device.repository.DeviceRepository;
import com.example.senioron.global.config.FirebaseConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

class FcmSenderSeniorPayloadTest {
    @Test
    void normalAndSosPushIncludeEventAndSeniorIdsAsStrings() throws Exception {
        FirebaseConfig config = mock(FirebaseConfig.class);
        given(config.isInitialized()).willReturn(true);
        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        given(messaging.send(any(Message.class))).willReturn("message-id");
        var metrics = new SimpleMeterRegistry();
        try (var staticMessaging = mockStatic(FirebaseMessaging.class)) {
            staticMessaging.when(FirebaseMessaging::getInstance).thenReturn(messaging);
            FcmSender sender = new FcmSender(config, mock(DeviceRepository.class),
                    mock(ApplicationContext.class), metrics);
            assertThat(sender.send("child-token", "알림", "본문", 123L, 100L)).isTrue();
            assertThat(sender.sendHighPriority("child-token", "SOS", "본문", 124L, 200L)).isTrue();
            ArgumentCaptor<Message> captured = ArgumentCaptor.forClass(Message.class);
            verify(messaging, times(2)).send(captured.capture());
            assertThat(data(captured.getAllValues().get(0)))
                    .containsEntry("eventId", "123").containsEntry("seniorId", "100");
            assertThat(data(captured.getAllValues().get(1)))
                    .containsEntry("eventId", "124").containsEntry("seniorId", "200");
        } finally {
            metrics.close();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> data(Message message) {
        return (Map<String, String>) ReflectionTestUtils.getField(message, "data");
    }
}
