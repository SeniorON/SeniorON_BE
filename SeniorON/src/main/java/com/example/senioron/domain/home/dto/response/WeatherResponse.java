package com.example.senioron.domain.home.dto.response;

import java.time.LocalDateTime;

public record WeatherResponse(
        int temperature,
        String weatherStatus,
        String weatherText,
        LocalDateTime observedAt
) {
}