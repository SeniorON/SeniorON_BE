package com.example.senioron.domain.companion.service.model;

import java.util.Arrays;
import java.util.Objects;

public record VoiceAudio (
        String filename,
        String contentType,
        byte[] bytes
){

    public VoiceAudio {
        Objects.requireNonNull(filename);
        Objects.requireNonNull(contentType);
        Objects.requireNonNull(bytes);

        bytes = Arrays.copyOf(bytes,bytes.length);
    }

    @Override
    public byte[] bytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }
}
