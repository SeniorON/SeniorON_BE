package com.example.senioron.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class SafeBrowsingConfig {

    @Bean
    public RestClient safeBrowsingRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3000);   //타임아웃 설정
        requestFactory.setReadTimeout(5000);      // 타임아웃 설정

        return RestClient.builder()
                .baseUrl("https://safebrowsing.googleapis.com")
                .requestFactory(requestFactory)
                .build();
    }
}