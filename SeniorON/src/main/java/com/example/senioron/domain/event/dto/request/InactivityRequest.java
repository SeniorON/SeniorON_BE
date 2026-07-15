package com.example.senioron.domain.event.dto.request;

import jakarta.validation.constraints.*;
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

    @NotNull @Min(0) @Max(100)
    private Integer deviceBattery;

    @NotNull @PastOrPresent
    private LocalDateTime lastSeenAt;
}
