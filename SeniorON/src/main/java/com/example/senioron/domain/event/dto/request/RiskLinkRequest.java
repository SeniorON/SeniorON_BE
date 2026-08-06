package com.example.senioron.domain.event.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
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
    @Schema(
            description = "검사할 URL. 실제 위험 판정 테스트는 Google Safe Browsing 공식 테스트 URL을 사용한다",
            example = "http://testsafebrowsing.appspot.com/s/phishing.html"
    )
    private String linkUrl;

    @Min(0) @Max(100)
    private Integer deviceBattery;
}
