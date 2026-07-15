package com.example.senioron.domain.event.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
public class InactivityRequest {
    @NotNull
    private BigDecimal latitude;
    @NotNull
    private BigDecimal longitude;
    private LocalDateTime LastSeenAt;
    private Integer deviceBattery;
}
