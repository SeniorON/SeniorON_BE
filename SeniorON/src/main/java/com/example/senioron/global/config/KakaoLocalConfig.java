package com.example.senioron.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

@Configuration
public class KakaoLocalConfig {
    @Bean
    public RestClient kakaoRestClient(){
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(1000);
        requestFactory.setReadTimeout(2000);

        return RestClient.builder()
                .baseUrl("https://dapi.kakao.com")
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    public ThreadPoolTaskExecutor sosAddressExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("sos-address-");
        executor.setAwaitTerminationSeconds(5);
        // 기본 AbortPolicy 유지: 포화 시 SOS 요청 스레드에서 대신 실행하지 않는다.
        return executor;
    }
}
