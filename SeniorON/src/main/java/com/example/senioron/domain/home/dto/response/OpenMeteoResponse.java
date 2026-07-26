package com.example.senioron.domain.home.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OpenMeteoResponse(
        Current current
) {

    public record Current(
            String time,

            @JsonProperty("temperature_2m")
            Double temperature,

            @JsonProperty("weather_code")
            Integer weatherCode
    ) {
    }
}