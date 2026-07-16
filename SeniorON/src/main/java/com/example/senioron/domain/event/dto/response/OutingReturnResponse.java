package com.example.senioron.domain.event.dto.response;

import com.example.senioron.domain.event.entity.Event;
import com.example.senioron.domain.event.entity.OutingPhase;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class OutingReturnResponse {

    private Long id;
    private OutingPhase phase;
    private LocalDateTime occurredAt;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String address;
    private Integer deviceBattery;

    public static OutingReturnResponse of(Event event) {
        return OutingReturnResponse.builder()
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