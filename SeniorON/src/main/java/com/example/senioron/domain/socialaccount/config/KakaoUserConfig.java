package com.example.senioron.domain.socialaccount.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class KakaoUserConfig {

    @Bean("kakaoUserRestClient")
    public RestClient kakaoUserRestClient(
            @Value("${kakao.user.connect-timeout}") Duration connectTimeout,
            @Value("${kakao.user.read-timeout}") Duration readTimeout
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) connectTimeout.toMillis());
        requestFactory.setReadTimeout((int) readTimeout.toMillis());

        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }
}
