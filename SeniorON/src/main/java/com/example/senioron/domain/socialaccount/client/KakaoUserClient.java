package com.example.senioron.domain.socialaccount.client;

import com.example.senioron.domain.socialaccount.dto.kakao.response.KakaoUserInfo;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class KakaoUserClient {

    /**
     * 카카오 Access Token으로 사용자 정보를 조회합니다.
     */
    public KakaoUserInfo getUserInfo(String kakaoAccessToken) {
        return RestClient.create()
                .get()
                .uri("https://kapi.kakao.com/v2/user/me")
                .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + kakaoAccessToken
                )
                .retrieve()
                .body(KakaoUserInfo.class);
    }
}
