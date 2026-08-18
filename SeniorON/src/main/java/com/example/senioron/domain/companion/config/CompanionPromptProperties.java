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
@ConfigurationProperties(prefix = "companion.prompt")
public class CompanionPromptProperties {

    private Resource resource = new ClassPathResource("prompts/companion-system-prompt.txt");

    private String version = "v2";

    @Min(1)
    @Max(20)
    private int recentMessageLimit = 20;
}