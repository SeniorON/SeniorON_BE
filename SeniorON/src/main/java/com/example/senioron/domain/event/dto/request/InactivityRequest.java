package com.example.senioron.domain.event.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
public class InactivityRequest {
    @NotNull
    @DecimalMin(value = "-90", inclusive = true)
    @DecimalMax(value = "90", inclusive = true)
    private BigDecimal latitude;

    @NotNull
    @DecimalMin(value = "-180", inclusive = true)
    @DecimalMax(value = "180", inclusive = true)
    private BigDecimal longitude;

    private Integer deviceBattery;
    private LocalDateTime lastSeenAt;
}
