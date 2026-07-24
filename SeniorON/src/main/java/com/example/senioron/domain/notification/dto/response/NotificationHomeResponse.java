package com.example.senioron.domain.notification.dto.response;

import com.example.senioron.domain.event.entity.Event;
import com.example.senioron.domain.event.entity.OutingPhase;
import com.example.senioron.domain.notification.entity.Notification;
import com.example.senioron.domain.notification.entity.NotificationType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Builder
@Getter
public class NotificationHomeResponse {
    private NotificationType type;
    private boolean enabled;
    private boolean hasAlert;

    private LocalDateTime occurredAt;
    private String dateTimeLabel;
    private String summary;

    private Long senderId;
    private String senderName;
    private Integer deviceBattery;
    private String address;
    private String linkUrl;
    private OutingPhase phase;

    private String emptyMessage;

    public static NotificationHomeResponse of(NotificationType type, boolean enabled, Notification latest) {
        if (latest == null) {
            return NotificationHomeResponse.builder()
                    .type(type)
                    .enabled(enabled)
                    .hasAlert(false)
                    .emptyMessage(resolveEmptyMessage(type))
                    .build();
        } else {
            Event event = latest.getEvent();
            String senderName = latest.getSendUser().getName();
            String address = event != null ? event.getAddress() : null;
            LocalDateTime referenceTime = (type == NotificationType.INACTIVITY && event != null && event.getLastSeenAt() != null)
                    ? event.getLastSeenAt()
                    : latest.getCreatedAt();
            boolean withSinceSuffix = type == NotificationType.INACTIVITY;
            return NotificationHomeResponse.builder()
                    .type(type)
                    .enabled(enabled)
                    .hasAlert(true)
                    .occurredAt(latest.getCreatedAt())
                    .dateTimeLabel(resolveDateTimeLabel(referenceTime, withSinceSuffix))
                    .summary(latest.getBody())
                    .senderId(latest.getSendUser().getUsersId())
                    .senderName(senderName)
                    .deviceBattery(event != null ? event.getDeviceBattery() : null)
                    .address(address)
                    .linkUrl(latest.getLinkUrl())
                    .phase(event != null ? event.getPhase() : null)
                    .build();
        }
    }

    // "오늘 오전 10:00" / "어제 오전 10:00부터" / "7월 20일 오전 10:00"
    private static String resolveDateTimeLabel(LocalDateTime dateTime, boolean withSinceSuffix) {
        if (dateTime == null) {
            return null;
        }
        LocalDate date = dateTime.toLocalDate();
        LocalDate today = LocalDate.now();
        String dayLabel;
        if (date.isEqual(today)) {
            dayLabel = "오늘";
        } else if (date.isEqual(today.minusDays(1))) {
            dayLabel = "어제";
        } else {
            dayLabel = date.getMonthValue() + "월 " + date.getDayOfMonth() + "일";
        }
        String label = dayLabel + " " + resolveTimeLabel(dateTime);
        return withSinceSuffix ? label + "부터" : label;
    }

    // "오전 10:00" / "오후 3:05"
    private static String resolveTimeLabel(LocalDateTime dateTime) {
        int hour = dateTime.getHour();
        String period = hour < 12 ? "오전" : "오후";
        int displayHour = hour % 12 == 0 ? 12 : hour % 12;
        return String.format("%s %d:%02d", period, displayHour, dateTime.getMinute());
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

