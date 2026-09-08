package com.example.senioron.domain.event.dto.response;

import com.example.senioron.domain.event.entity.Event;
import com.example.senioron.domain.event.entity.EventType;
import com.example.senioron.domain.event.entity.OutingPhase;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Getter
@Builder
public class EventDetailResponse {
    private static final ZoneId KOREA_ZONE_ID = ZoneId.of("Asia/Seoul");

    private Long eventId;
    private EventType eventType;
    private String message;
    private String senderName;
    private OffsetDateTime occurredAt;

    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;

    private Integer deviceBattery;

    private OutingPhase phase;           // OUTING_RETURN 전용
    private LocalDateTime lastSeenAt;    // INACTIVITY 전용
    private String linkUrl;              // RISK_LINK 전용
    private Boolean isDangerous;         // RISK_LINK 전용

    public static EventDetailResponse of(Event event, Integer currentBatteryLevel) {
        return EventDetailResponse.builder()
                .eventId(event.getEventId())
                .eventType(event.getEventType())
                .message(resolveMessage(event.getEventType(), event.getPhase(), event.getIsDangerous()))
                .senderName(event.getTriggeredUser().getName())
                .occurredAt(toOffsetDateTime(event.getCreatedAt()))
                .address(event.getAddress())
                .latitude(event.getLatitude())
                .longitude(event.getLongitude())
                .deviceBattery(currentBatteryLevel)
                .phase(event.getPhase())
                .lastSeenAt(event.getLastSeenAt())
                .linkUrl(event.getLinkUrl())
                .isDangerous(event.getIsDangerous())
                .build();
    }

    private static OffsetDateTime toOffsetDateTime(LocalDateTime occurredAt) {
        return occurredAt == null
                ? null
                : occurredAt.atZone(KOREA_ZONE_ID).toOffsetDateTime();
    }

    private static String resolveMessage(EventType eventType, OutingPhase phase, Boolean isDangerous) {
        return switch (eventType) {
            case SOS -> "도움이 필요해요";
            case INACTIVITY -> "무활동 감지됨";
            case RISK_LINK -> resolveRiskLinkMessage(isDangerous);
            case OUTING_RETURN -> phase == OutingPhase.OUTING ? "외출하셨어요" : "귀가하셨어요";
        };
    }

    private static String resolveRiskLinkMessage(Boolean isDangerous) {
        if (isDangerous == null) {
            return "링크 위험 여부를 확인하지 못했어요";
        }
        return isDangerous ? "위험한 링크가 감지됐어요" : "안전한 링크로 확인됐어요";
    }
}
