package com.example.senioron.domain.companion.service.model;

public record TranscriptionResult(
        String text,
        String provider,
        String model
) {
}