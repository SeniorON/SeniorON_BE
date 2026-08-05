package com.example.senioron.domain.socialaccount.service;

import com.example.senioron.domain.socialaccount.dto.kakao.response.KakaoUserInfo;
import com.example.senioron.domain.socialaccount.dto.kakao.request.KakaoLoginRequest;
import com.example.senioron.domain.socialaccount.dto.kakao.response.KakaoLoginResponse;
import com.example.senioron.domain.socialaccount.entity.LoginProvider;
import com.example.senioron.domain.socialaccount.entity.SocialAccount;
import com.example.senioron.domain.socialaccount.repository.SocialAccountRepository;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.entity.UserStatus;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import com.example.senioron.domain.user.service.RefreshTokenService;
import com.example.senioron.global.jwt.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KakaoLoginService {

    private final SocialAccountRepository socialAccountRepository;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;

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


    /**
     * 카카오 로그인 처리
     * - 기존 회원이면 JWT를 발급합니다.
     * - 신규 회원이면 추가 회원가입을 위한 정보를 반환합니다.
     */
    @Transactional
    public KakaoLoginResponse kakaoLogin(KakaoLoginRequest request) {

        KakaoUserInfo kakaoUserInfo =
                getUserInfo(request.getKakaoAccessToken());

        validateRequiredInfo(kakaoUserInfo);

        String providerId = String.valueOf(kakaoUserInfo.getId());

        Optional<SocialAccount> existingSocialAccount =
                socialAccountRepository.findByProviderAndProviderId(
                        LoginProvider.KAKAO,
                        providerId
                );

        // 기존 카카오 회원인 경우 JWT 발급 후 로그인
        if (existingSocialAccount.isPresent()) {
            User user = existingSocialAccount.get().getUser();
            if (user.getStatus() == UserStatus.WITHDRAWN) {
                throw new BusinessException(ErrorCode.WITHDRAWN_USER);
            }

            String accessToken = jwtUtil.createAccessToken(user);
            String refreshToken = jwtUtil.createRefreshToken(user);
            refreshTokenService.saveOrRotate(user, request.getDeviceIdentifier(), refreshToken);

            return KakaoLoginResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .usersId(user.getUsersId())
                    .name(user.getName())
                    .role(user.getRole())
                    .newUser(false)
                    .build();
        }

        // 신규 카카오 회원인 경우 추가 회원가입 진행
        return KakaoLoginResponse.builder()
                .accessToken(null)
                .usersId(null)
                .name(kakaoUserInfo.getNickname())
                .role(null)
                .providerId(providerId)
                .newUser(true)
                .build();
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
