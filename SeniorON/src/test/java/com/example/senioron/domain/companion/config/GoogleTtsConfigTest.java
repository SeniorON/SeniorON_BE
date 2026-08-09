package com.example.senioron.domain.companion.config;

import com.google.cloud.texttospeech.v1.TextToSpeechSettings;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleTtsConfigTest {

    @Test
    void 설정한_timeout을_Google_SDK에_적용한다()
            throws Exception {
        GoogleTtsProperties properties =
                new GoogleTtsProperties();

        properties.setRequestTimeout(
                Duration.ofSeconds(12)
        );

        GoogleTtsConfig config =
                new GoogleTtsConfig();

        TextToSpeechSettings settings =
                config.googleTextToSpeechSettings(
                        properties
                );

        var retrySettings =
                settings.synthesizeSpeechSettings()
                        .getRetrySettings();

        assertThat(
                retrySettings
                        .getInitialRpcTimeoutDuration()
        ).isEqualTo(Duration.ofSeconds(12));

        assertThat(
                retrySettings
                        .getMaxRpcTimeoutDuration()
        ).isEqualTo(Duration.ofSeconds(12));

        assertThat(
                retrySettings
                        .getTotalTimeoutDuration()
        ).isEqualTo(Duration.ofSeconds(12));
    }
}