package com.example.senioron.domain.event.dto.request;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
public class SosEventRequest {
    private LocalDateTime occurredAt;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Integer deviceBattery;

    @Builder
    public SosEventRequest(LocalDateTime occurredAt, BigDecimal latitude, BigDecimal longitude, Integer deviceBattery) {
        this.occurredAt = occurredAt;
        this.latitude = latitude;
        this.longitude = longitude;
        this.deviceBattery = deviceBattery;
    }
}
