package com.example.senioron.domain.event.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class RiskLinkRequest {
    @NotBlank
    private String linkUrl;

    @Min(0) @Max(100)
    private Integer deviceBattery;
}
