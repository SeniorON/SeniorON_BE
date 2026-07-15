package com.example.senioron.domain.event.dto.request;

import com.example.senioron.domain.event.entity.OutingPhase;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class OutingReturnRequest{

    @NotNull
    private OutingPhase phase;

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

    @Builder
    public OutingReturnRequest(OutingPhase phase, BigDecimal latitude, BigDecimal longitude, Integer deviceBattery) {
        this.phase = phase;
        this.latitude = latitude;
        this.longitude = longitude;
        this.deviceBattery = deviceBattery;
    }
}