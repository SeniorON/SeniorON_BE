package com.example.senioron.domain.companion.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(
        prefix = "companion.safety"
)
public class CompanionSafetyProperties {

    private Resource promptResource =
            new ClassPathResource(
                    "prompts/companion-safety-classifier-prompt.txt"
            );

    private String promptVersion =
            "safety-v1";

    @Min(1)
    @Max(10)
    private int recentMessageLimit = 6;

    @Min(32)
    @Max(256)
    private int maxTokens = 64;

    private String notificationTitle =
            "말벗 안전 확인 알림";

    private String notificationBody =
            "부모님의 안전 확인이 필요합니다. 직접 연락해 상태를 확인해 주세요.";
}