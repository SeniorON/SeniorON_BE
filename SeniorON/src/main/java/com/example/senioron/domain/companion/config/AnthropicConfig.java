package com.example.senioron.domain.companion.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class AnthropicConfig {

    @Bean("anthropicRestClient")
    public RestClient anthropicRestClient(
            AnthropicProperties properties
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(
                (int) properties
                        .getConnectTimeout()
                        .toMillis()
        );

        requestFactory.setReadTimeout(
                (int) properties
                        .getReadTimeout()
                        .toMillis()
        );

        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}