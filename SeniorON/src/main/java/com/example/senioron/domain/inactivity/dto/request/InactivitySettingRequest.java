package com.example.senioron.domain.inactivity.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

@Getter
public class InactivitySettingRequest {

    @NotNull
    @Min(1) @Max(24)
    private Integer thresholdHours;

    @Builder
    public InactivitySettingRequest(Integer thresholdHours) {
        this.thresholdHours = thresholdHours;
    }
}
