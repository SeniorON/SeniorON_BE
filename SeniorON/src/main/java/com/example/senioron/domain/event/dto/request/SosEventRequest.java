package com.example.senioron.domain.event.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
public class SosEventRequest {
    @NotNull
    private BigDecimal latitude;
    @NotNull
    private BigDecimal longitude;
    private Integer deviceBattery;

    @Builder
    public SosEventRequest(LocalDateTime occurredAt, BigDecimal latitude, BigDecimal longitude, Integer deviceBattery) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.deviceBattery = deviceBattery;
    }
}
