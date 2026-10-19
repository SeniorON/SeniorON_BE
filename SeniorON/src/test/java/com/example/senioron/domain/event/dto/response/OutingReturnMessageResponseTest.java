package com.example.senioron.domain.event.dto.response;

import com.example.senioron.domain.event.entity.*;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.notification.service.NotificationService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OutingReturnMessageResponseTest {
    @ParameterizedTest
    @CsvSource({
            "OUTING,TRANSITION,외출하셨어요",
            "RETURN,TRANSITION,귀가하셨어요",
            "OUTING,RECONNECTION,집 밖에 있는 것이 확인됐어요",
            "RETURN,RECONNECTION,집에 있는 것이 확인됐어요"
    })
    void detailAndNotificationUseSameMessage(OutingPhase phase, OutingDetectionType type, String message) {
        var event = Event.builder().eventType(EventType.OUTING_RETURN).phase(phase).detectionType(type)
                .triggeredUser(User.builder().name("부모님").build()).build();
        assertThat(EventDetailResponse.of(event).getMessage()).isEqualTo(message);
        var service = mock(NotificationService.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        String body = ReflectionTestUtils.invokeMethod(service, "resolveBody", event);
        assertThat(body).isEqualTo(message);
    }

    @Test
    void legacyDetectionTypeUsesTransitionMessageAndCreatedTimestamp() {
        var event = Event.builder().eventType(EventType.OUTING_RETURN).phase(OutingPhase.OUTING)
                .triggeredUser(User.builder().name("부모님").build()).build();
        var createdAt = LocalDateTime.parse("2026-10-19T14:00:00");
        ReflectionTestUtils.setField(event, "createdAt", createdAt);
        assertThat(EventDetailResponse.of(event).getMessage()).isEqualTo("외출하셨어요");
        assertThat(EventDetailResponse.of(event).getOccurredAt().toLocalDateTime()).isEqualTo(createdAt);
        assertThat(OutingReturnResponse.of(event).getOccurredAt()).isEqualTo(createdAt);
    }

    @Test
    void outingDetailPrefersDetectedAtButOtherTypesKeepCreatedAt() {
        var detectedAt = LocalDateTime.parse("2026-10-19T13:00:00");
        var createdAt = detectedAt.plusHours(1);
        var event = Event.builder().eventType(EventType.OUTING_RETURN).phase(OutingPhase.RETURN)
                .detectionType(OutingDetectionType.RECONNECTION).detectedAt(detectedAt).clientEventId("id-1")
                .triggeredUser(User.builder().name("부모님").build()).build();
        ReflectionTestUtils.setField(event, "createdAt", createdAt);
        var detail = EventDetailResponse.of(event);
        assertThat(detail.getOccurredAt().toLocalDateTime()).isEqualTo(detectedAt);
        assertThat(detail.getDetectedAt().getOffset()).isEqualTo(java.time.ZoneOffset.ofHours(9));
        assertThat(detail.getClientEventId()).isEqualTo("id-1");
        assertThat(detail.getDetectionType()).isEqualTo(OutingDetectionType.RECONNECTION);
        ReflectionTestUtils.setField(event, "eventType", EventType.SOS);
        assertThat(EventDetailResponse.of(event).getOccurredAt().toLocalDateTime()).isEqualTo(createdAt);
    }
}
