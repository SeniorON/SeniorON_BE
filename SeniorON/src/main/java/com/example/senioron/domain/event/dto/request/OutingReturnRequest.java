package com.example.senioron.domain.event.dto.request;

import com.example.senioron.domain.event.entity.OutingPhase;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Getter;


import java.math.BigDecimal;

@Getter
public class OutingReturnRequest {

    @NotNull
    private OutingPhase phase;

    @NotNull
    @DecimalMin(value = "-90", inclusive = true)
    @DecimalMax(value = "90", inclusive = true)
    @Schema(example = "37.5665")
    private BigDecimal latitude;

    @NotNull
    @DecimalMin(value = "-180", inclusive = true)
    @DecimalMax(value = "180", inclusive = true)
    @Schema(example = "126.9780")
    private BigDecimal longitude;

    @NotNull @Min(0) @Max(100)
    private Integer deviceBattery;

    @Builder
    @JsonCreator
    public OutingReturnRequest(
            @JsonProperty("phase") OutingPhase phase,
            @JsonProperty("latitude") BigDecimal latitude,
            @JsonProperty("longitude") BigDecimal longitude,
            @JsonProperty("deviceBattery") Integer deviceBattery
    ) {
        this.phase = phase;
        this.latitude = latitude;
        this.longitude = longitude;
        this.deviceBattery = deviceBattery;
    }
}