package com.example.senioron.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class WeatherConfig {

    @Bean("weatherRestClient")
    public RestClient weatherRestClient() {
        return RestClient.builder()
                .baseUrl("https://api.open-meteo.com")
                .build();
    }
}