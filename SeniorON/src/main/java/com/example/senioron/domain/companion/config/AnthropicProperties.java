package com.example.senioron.domain.companion.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ai.llm.anthropic")
public class AnthropicProperties {

    private String baseUrl = "https://api.anthropic.com";

    private String apiKey = "";

    private String version = "2023-06-01";

    private String model = "claude-haiku-4-5-20251001";

    private int maxTokens = 256;

    private Duration connectTimeout = Duration.ofSeconds(3);

    private Duration readTimeout = Duration.ofSeconds(30);
}