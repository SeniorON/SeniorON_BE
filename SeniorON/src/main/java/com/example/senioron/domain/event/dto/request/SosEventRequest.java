package com.example.senioron.domain.event.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
public class SosEventRequest {
    @NotNull
    @Schema(example = "37.5665")
    private BigDecimal latitude;
    @NotNull
    @Schema(example = "126.9780")
    private BigDecimal longitude;
    private Integer deviceBattery;
}
