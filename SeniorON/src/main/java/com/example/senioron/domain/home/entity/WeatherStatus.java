package com.example.senioron.domain.home.entity;

import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;

public enum WeatherStatus {

    CLEAR("맑음"),
    PARTLY_CLOUDY("구름 조금"),
    CLOUDY("흐림"),
    FOG("안개"),
    RAIN("비"),
    SNOW("눈"),
    SHOWER("소나기"),
    THUNDERSTORM("뇌우");

    private final String description;

    WeatherStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public static WeatherStatus fromCode(int code) {

        if (code == 0) {
            return CLEAR;
        }

        if (code == 1 || code == 2) {
            return PARTLY_CLOUDY;
        }

        if (code == 3) {
            return CLOUDY;
        }

        if (code == 45 || code == 48) {
            return FOG;
        }

        if ((code >= 51 && code <= 67)
                || (code >= 80 && code <= 82)) {
            return RAIN;
        }

        if (code >= 71 && code <= 77) {
            return SNOW;
        }

        if (code >= 95 && code <= 99) {
            return THUNDERSTORM;
        }

        throw new BusinessException(
                ErrorCode.WEATHER_DATA_NOT_SUPPORTED
        );
    }
}