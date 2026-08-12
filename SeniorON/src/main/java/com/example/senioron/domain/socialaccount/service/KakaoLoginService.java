package com.example.senioron.domain.socialaccount.service;

import com.example.senioron.domain.socialaccount.client.KakaoUserClient;
import com.example.senioron.domain.socialaccount.dto.kakao.response.KakaoUserInfo;
import com.example.senioron.domain.socialaccount.dto.kakao.request.KakaoLoginRequest;
import com.example.senioron.domain.socialaccount.dto.kakao.response.KakaoLoginResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KakaoLoginService {

    private final KakaoUserClient kakaoUserClient;
    private final KakaoLoginTransactionService kakaoLoginTransactionService;

    /**
     * 카카오 로그인 처리
     * - 기존 회원이면 JWT를 발급합니다.
     * - 신규 회원이면 추가 회원가입을 위한 정보를 반환합니다.
     */
    public KakaoLoginResponse kakaoLogin(KakaoLoginRequest request) {

        KakaoUserInfo kakaoUserInfo =
                kakaoUserClient.getUserInfo(request.getKakaoAccessToken());

        validateRequiredInfo(kakaoUserInfo);

        return kakaoLoginTransactionService.login(request, kakaoUserInfo);
    }

    private void validateRequiredInfo(KakaoUserInfo kakaoUserInfo) {

        if (kakaoUserInfo == null
                || kakaoUserInfo.getId() == null
                || kakaoUserInfo.getNickname() == null) {

            throw new IllegalArgumentException(
                    "카카오 사용자 정보를 가져오지 못했습니다."
            );
        }
    }

}
