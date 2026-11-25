package com.example.senioron.domain.socialaccount.config;

import com.example.senioron.domain.socialaccount.client.KakaoRestClientFactory;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class KakaoUserConfig {

    @Bean("kakaoUserRestClient")
    public RestClient kakaoUserRestClient(
            @Value("${kakao.user.connect-timeout}") Duration connectTimeout,
            @Value("${kakao.user.read-timeout}") Duration readTimeout
    ) {
        return KakaoRestClientFactory.create(connectTimeout, readTimeout);
    }
}
