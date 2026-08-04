package com.example.senioron.domain.home.service;

import com.example.senioron.domain.home.dto.response.OpenMeteoResponse;
import com.example.senioron.domain.home.dto.response.WeatherResponse;
import com.example.senioron.domain.home.entity.WeatherStatus;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

@Service
public class WeatherService {

    private final RestClient weatherRestClient;

    public WeatherService(
            @Qualifier("weatherRestClient")
            RestClient weatherRestClient
    ) {
        this.weatherRestClient = weatherRestClient;
    }

    public WeatherResponse getCurrentWeather(
            double latitude,
            double longitude
    ) {

        long totalStart = System.currentTimeMillis();

        long start = System.currentTimeMillis();

        validateCoordinates(
                latitude,
                longitude
        );

        System.out.println("validateCoordinates : "
                + (System.currentTimeMillis() - start) + "ms");

        OpenMeteoResponse response;

        start = System.currentTimeMillis();

        try {
            response = weatherRestClient
                    .get()
                    .uri(uriBuilder ->
                            uriBuilder
                                    .path("/v1/forecast")
                                    .queryParam(
                                            "latitude",
                                            latitude
                                    )
                                    .queryParam(
                                            "longitude",
                                            longitude
                                    )
                                    .queryParam(
                                            "current",
                                            "temperature_2m,weather_code"
                                    )
                                    .queryParam(
                                            "timezone",
                                            "Asia/Seoul"
                                    )
                                    .build()
                    )
                    .retrieve()
                    .body(OpenMeteoResponse.class);

        } catch (Exception e) {
            throw new BusinessException(
                    ErrorCode.WEATHER_API_CALL_FAILED
            );
        }

        System.out.println("OpenMeteo API : "
                + (System.currentTimeMillis() - start) + "ms");

        start = System.currentTimeMillis();

        validateWeatherResponse(
                response
        );

        WeatherStatus weatherStatus =
                WeatherStatus.fromCode(
                        response.current().weatherCode()
                );

        LocalDateTime observedAt =
                parseObservedAt(
                        response.current().time()
                );

        System.out.println("Response Parsing : "
                + (System.currentTimeMillis() - start) + "ms");

        System.out.println("getCurrentWeather TOTAL : "
                + (System.currentTimeMillis() - totalStart) + "ms");

        return new WeatherResponse(
                (int) Math.round(
                        response.current().temperature()
                ),
                weatherStatus.name(),
                weatherStatus.getDescription(),
                observedAt
        );
    }

    private void validateCoordinates(
            double latitude,
            double longitude
    ) {

        if (!Double.isFinite(latitude)
                || !Double.isFinite(longitude)
                || latitude < -90
                || latitude > 90
                || longitude < -180
                || longitude > 180) {

            throw new BusinessException(
                    ErrorCode.INVALID_LOCATION_COORDINATES
            );
        }
    }

    private void validateWeatherResponse(
            OpenMeteoResponse response
    ) {

        if (response == null
                || response.current() == null
                || response.current().temperature() == null
                || response.current().weatherCode() == null
                || response.current().time() == null) {

            throw new BusinessException(
                    ErrorCode.WEATHER_DATA_NOT_FOUND
            );
        }
    }

    private LocalDateTime parseObservedAt(
            String observedAt
    ) {

        try {
            return LocalDateTime.parse(
                    observedAt
            );

        } catch (DateTimeParseException e) {
            throw new BusinessException(
                    ErrorCode.WEATHER_DATA_NOT_FOUND
            );
        }
    }
}