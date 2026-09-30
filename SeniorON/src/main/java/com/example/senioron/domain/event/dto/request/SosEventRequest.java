package com.example.senioron.domain.event.dto.request;

import com.example.senioron.global.validation.CoordinatePairRequest;
import com.example.senioron.global.validation.ValidCoordinatePair;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@ValidCoordinatePair
public class SosEventRequest implements CoordinatePairRequest {
    @Schema(example = "37.5665")
    private BigDecimal latitude;
    @Schema(example = "126.9780")
    private BigDecimal longitude;
    private Integer deviceBattery;

    @Override
    public BigDecimal latitude() {
        return latitude;
    }

    @Override
    public BigDecimal longitude() {
        return longitude;
    }
}
