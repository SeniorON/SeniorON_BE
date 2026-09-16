package com.example.senioron.domain.event.dto.response;

import com.example.senioron.domain.event.entity.Event;
import com.example.senioron.domain.notification.dto.NotificationPreparationResult;
import io.swagger.v3.oas.annotations.media.Schema;
import com.example.senioron.domain.event.entity.OutingPhase;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class OutingReturnResponse {
    private Long seniorId;
    @Schema(description = "발송 준비 상태이며 FCM 전달 또는 기기 수신 성공을 의미하지 않습니다.")
    private NotificationPreparationResult.Status notificationStatus;
    private String reason;
    private int receiverCount;


    private Long id;
    private OutingPhase phase;
    private LocalDateTime occurredAt;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String address;
    private Integer deviceBattery;

    public static OutingReturnResponse of(Event event, NotificationPreparationResult result) {
        OutingReturnResponse response = of(event);
        response.notificationStatus = result.notificationStatus();
        response.reason = result.reason();
        response.receiverCount = result.receiverCount();
        return response;
    }

    public static OutingReturnResponse of(Event event) {
        return OutingReturnResponse.builder()
                .seniorId(event.getSenior() == null ? null : event.getSenior().getSeniorId())
                .id(event.getEventId())
                .phase(event.getPhase())
                .occurredAt(event.getCreatedAt())
                .latitude(event.getLatitude())
                .longitude(event.getLongitude())
                .address(event.getAddress())
                .deviceBattery(event.getDeviceBattery())
                .build();
    }
}
