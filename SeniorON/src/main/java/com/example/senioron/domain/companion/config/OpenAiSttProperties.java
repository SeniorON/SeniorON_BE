package com.example.senioron.domain.companion.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ai.stt.openai")
public class OpenAiSttProperties {

    private String baseUrl = "https://api.openai.com";
    private String apiKey = "";
    private String model = "gpt-4o-mini-transcribe";
    private String language = "ko";
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(30);
}
