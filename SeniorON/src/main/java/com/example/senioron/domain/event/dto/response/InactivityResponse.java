package com.example.senioron.domain.event.dto.response;

import com.example.senioron.domain.event.entity.Event;
import com.example.senioron.domain.notification.dto.NotificationPreparationResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class InactivityResponse {
    private Long seniorId;
    @Schema(description = "발송 준비 상태이며 FCM 전달 또는 기기 수신 성공을 의미하지 않습니다.")
    private NotificationPreparationResult.Status notificationStatus;
    private String reason;
    private int receiverCount;

    private Long id;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private LocalDateTime LastSeenAt;

    public static InactivityResponse of(Event event, NotificationPreparationResult result) {
        InactivityResponse response = of(event);
        response.notificationStatus = result.notificationStatus();
        response.reason = result.reason();
        response.receiverCount = result.receiverCount();
        return response;
    }

    public static InactivityResponse of(Event event) {
        return InactivityResponse.builder()
                .seniorId(event.getSenior() == null ? null : event.getSenior().getSeniorId())
                .id(event.getEventId())
                .address(event.getAddress())
                .latitude(event.getLatitude())
                .longitude(event.getLongitude())
                .LastSeenAt(event.getLastSeenAt())
                .build();
    }
}
