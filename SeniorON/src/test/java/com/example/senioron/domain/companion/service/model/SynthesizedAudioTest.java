package com.example.senioron.domain.companion.service.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SynthesizedAudioTest {

    @Test
    void byte_배열을_방어적으로_복사한다() {
        byte[] original = {1, 2, 3};

        SynthesizedAudio audio =
                new SynthesizedAudio(
                        original,
                        "audio/mpeg",
                        "mp3",
                        "GOOGLE_CLOUD",
                        "ko-KR-Neural2-A"
                );

        original[0] = 9;

        byte[] returned = audio.bytes();
        returned[1] = 9;

        assertThat(audio.bytes())
                .containsExactly(1, 2, 3);
    }
}