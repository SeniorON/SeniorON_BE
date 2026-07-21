package com.example.senioron.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class SafeBrowsingConfig {
    @Bean
    public RestClient safeBrowsingRestClient(){
        return RestClient.builder()
                .baseUrl("https://safebrowsing.googleapis.com")
                .build();
    }
}
