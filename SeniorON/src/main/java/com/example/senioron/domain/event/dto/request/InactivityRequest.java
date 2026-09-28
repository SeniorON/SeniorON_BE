package com.example.senioron.domain.event.dto.request;

import com.example.senioron.global.validation.CoordinatePairRequest;
import com.example.senioron.global.validation.ValidCoordinatePair;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@ValidCoordinatePair
public class InactivityRequest implements CoordinatePairRequest {
    @DecimalMin(value = "-90", inclusive = true)
    @DecimalMax(value = "90", inclusive = true)
    @Schema(example = "37.5665")
    private BigDecimal latitude;

    @DecimalMin(value = "-180", inclusive = true)
    @DecimalMax(value = "180", inclusive = true)
    @Schema(example = "126.9780")
    private BigDecimal longitude;

    @NotNull @Min(0) @Max(100)
    private Integer deviceBattery;

    @NotNull @PastOrPresent
    private LocalDateTime lastSeenAt;

    @Override
    public BigDecimal latitude() {
        return latitude;
    }

    @Override
    public BigDecimal longitude() {
        return longitude;
    }
}
