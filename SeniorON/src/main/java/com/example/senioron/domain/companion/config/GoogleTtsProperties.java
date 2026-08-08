package com.example.senioron.domain.companion.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ai.tts.google")
public class GoogleTtsProperties {

    private String languageCode = "ko-KR";
    private String voiceName = "ko-KR-Neural2-A";
    private String audioEncoding = "MP3";

    private double speakingRate = 0.9;
    private double pitch = 0.0;
    private double volumeGainDb = 0.0;

    private Duration requestTimeout = Duration.ofSeconds(30);
}