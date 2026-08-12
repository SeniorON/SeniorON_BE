package com.example.senioron.domain.socialaccount.client;

import com.example.senioron.domain.socialaccount.dto.kakao.response.KakaoUserInfo;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class KakaoUserClient {

    private final RestClient restClient;
    private final String userInfoUrl;

    public KakaoUserClient(
            @Qualifier("kakaoUserRestClient") RestClient restClient,
            @Value("${kakao.user.info-url}") String userInfoUrl
    ) {
        this.restClient = restClient;
        this.userInfoUrl = userInfoUrl;
    }

    /**
     * 카카오 Access Token으로 사용자 정보를 조회합니다.
     */
    public KakaoUserInfo getUserInfo(String kakaoAccessToken) {
        return restClient
                .get()
                .uri(userInfoUrl)
                .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + kakaoAccessToken
                )
                .retrieve()
                .body(KakaoUserInfo.class);
    }
}
