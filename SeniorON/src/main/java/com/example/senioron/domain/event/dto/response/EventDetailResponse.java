package com.example.senioron.domain.event.dto.response;

import com.example.senioron.domain.event.entity.Event;
import com.example.senioron.domain.event.entity.EventType;
import com.example.senioron.domain.event.entity.OutingPhase;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class EventDetailResponse {
    private Long eventId;
    private EventType eventType;
    private String message;
    private String senderName;
    private LocalDateTime occurredAt;

    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;

    private Integer deviceBattery;

    private OutingPhase phase;           // OUTING_RETURN 전용
    private LocalDateTime lastSeenAt;    // INACTIVITY 전용

    public static EventDetailResponse of(Event event) {
        return EventDetailResponse.builder()
                .eventId(event.getEventId())
                .eventType(event.getEventType())
                .message(resolveMessage(event.getEventType(), event.getPhase()))
                .senderName(event.getTriggeredUser().getName())
                .occurredAt(event.getCreatedAt())
                .address(event.getAddress())
                .latitude(event.getLatitude())
                .longitude(event.getLongitude())
                .deviceBattery(event.getDeviceBattery())
                .phase(event.getPhase())
                .lastSeenAt(event.getLastSeenAt())
                .build();
    }
    private static String resolveMessage(EventType eventType, OutingPhase phase) {
        return switch (eventType) {
            case SOS -> "도움이 필요해요";
            case INACTIVITY -> "무활동 감지됨";
            case RISK_LINK -> "위험한 링크가 감지됐어요";
            case OUTING_RETURN -> phase == OutingPhase.OUTING ? "외출하셨어요" : "귀가하셨어요";
        };
    }
}
