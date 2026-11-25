package com.example.senioron.domain.socialaccount.client;

import com.example.senioron.domain.socialaccount.dto.kakao.response.KakaoAccessTokenInfo;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class KakaoAccessTokenInfoClient {

    private final RestClient restClient;
    private final String accessTokenInfoUrl;

    public KakaoAccessTokenInfoClient(
            @Qualifier("kakaoUserRestClient") RestClient restClient,
            @Value("${kakao.user.access-token-info-url}") String accessTokenInfoUrl
    ) {
        this.restClient = restClient;
        this.accessTokenInfoUrl = accessTokenInfoUrl;
    }

    /**
     * 카카오 Access Token의 유효성 및 발급 카카오 앱 정보를 조회합니다.
     */
    public KakaoAccessTokenInfo getAccessTokenInfo(String kakaoAccessToken) {
        return restClient
                .get()
                .uri(accessTokenInfoUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + kakaoAccessToken)
                .retrieve()
                .body(KakaoAccessTokenInfo.class);
    }
}
