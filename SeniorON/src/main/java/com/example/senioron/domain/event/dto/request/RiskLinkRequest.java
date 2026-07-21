package com.example.senioron.domain.event.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import org.hibernate.validator.constraints.URL;

@Getter
public class RiskLinkRequest {
    @NotBlank
    @Size(max = 2048)
    @URL(regexp = "^https?://.*", message = "http(s) 프로토콜의 URL만 허용됩니다")
    private String linkUrl;

    @Min(0) @Max(100)
    private Integer deviceBattery;
}
