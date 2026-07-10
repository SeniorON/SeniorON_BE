package com.example.senioron.domain.notification.dto.response;

import com.example.senioron.domain.notification.entity.Notification;
import com.example.senioron.domain.notification.entity.NotificationType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Builder
@Getter
public class NotificationHomeResponse {
    private NotificationType type;
    private boolean enabled;
    private boolean hasAlert;

    private LocalDateTime occurredAt;
    private String summary;

    private Long senderId;
    private String senderName;
    private Integer deviceBattery;

    private String emptyMessage;

    public static NotificationHomeResponse of(NotificationType type, boolean enabled, Notification latest) {
        if (latest == null) {
            return NotificationHomeResponse.builder()
                    .type(type)
                    .enabled(enabled)
                    .hasAlert(false)
                    .emptyMessage(resolveEmptyMessage(type))
                    .build();
        } else return NotificationHomeResponse.builder()
                .type(type)
                .enabled(enabled)
                .hasAlert(true)
                .occurredAt(latest.getCreatedAt())
                .summary(latest.getBody())
                .senderId(latest.getSendUser().getUsersId())
                .senderName(latest.getSendUser().getName())
                .deviceBattery(latest.getEvent() != null ? latest.getEvent().getDeviceBattery() : null)
                .build();
    }

    // 이벤트 없을 시
    private static String resolveEmptyMessage(NotificationType type) {
        return switch (type) {
            case SOS -> "감지된 SOS 알람이 없어요";
            case INACTIVITY -> "감지된 무활동 감지 알림이 없어요";
            case RISK_LINK -> "감지된 위험링크가 없어요";
            case OUTING_RETURN -> "감지된 외출,귀가 알림이 없어요";
            default -> "감지된 알림이 없어요";
        };
    }

}

