package com.example.senioron.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class KakaoLocalConfig {
    @Bean
    public RestClient kakaoRestClient(){
        return RestClient.builder()
                .baseUrl("https://dapi.kakao.com")
                .build();
    }
}
