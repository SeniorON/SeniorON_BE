package com.example.senioron.domain.companion.service.model;

import java.util.Arrays;
import java.util.Objects;

public record SynthesizedAudio(
        byte[] bytes,
        String contentType,
        String format,
        String provider,
        String voice
) {

    public SynthesizedAudio {
        Objects.requireNonNull(bytes);
        Objects.requireNonNull(contentType);
        Objects.requireNonNull(format);
        Objects.requireNonNull(provider);
        Objects.requireNonNull(voice);

        if (bytes.length == 0) {
            throw new IllegalArgumentException("합성된 음성 데이터는 비어 있을 수 없습니다.");
        }

        bytes = Arrays.copyOf(bytes, bytes.length);
    }

    @Override
    public byte[] bytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }
}