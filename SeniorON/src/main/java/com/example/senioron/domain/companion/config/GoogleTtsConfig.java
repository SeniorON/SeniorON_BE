package com.example.senioron.domain.companion.config;

import com.google.api.gax.retrying.RetrySettings;
import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1.TextToSpeechSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.io.IOException;
import java.time.Duration;

@Configuration
public class GoogleTtsConfig {

    @Bean
    public TextToSpeechSettings googleTextToSpeechSettings(
            GoogleTtsProperties properties
    ) throws IOException {
        Duration timeout =
                properties.getRequestTimeout();

        if (timeout == null
                || timeout.isZero()
                || timeout.isNegative()) {
            throw new IllegalStateException("Google TTS timeout은 0보다 커야 합니다.");
        }

        TextToSpeechSettings.Builder builder =
                TextToSpeechSettings.newBuilder();

        RetrySettings retrySettings =
                builder.synthesizeSpeechSettings()
                        .getRetrySettings()
                        .toBuilder()
                        .setInitialRpcTimeoutDuration(timeout)
                        .setMaxRpcTimeoutDuration(timeout)
                        .setTotalTimeoutDuration(timeout)
                        .build();

        builder.synthesizeSpeechSettings()
                .setRetrySettings(retrySettings);

        return builder.build();
    }

    @Bean(destroyMethod = "close")
    @Lazy
    public TextToSpeechClient googleTextToSpeechClient(
            TextToSpeechSettings settings
    ) throws IOException {
        return TextToSpeechClient.create(settings);
    }
}