package com.example.senioron.domain.socialaccount.service;

import com.example.senioron.domain.socialaccount.client.KakaoAuthorizationClient;
import com.example.senioron.domain.socialaccount.client.KakaoUserClient;
import com.example.senioron.domain.socialaccount.dto.kakao.request.KakaoWithdrawalLoginRequest;
import com.example.senioron.domain.socialaccount.dto.kakao.response.KakaoTokenResponse;
import com.example.senioron.domain.socialaccount.dto.kakao.response.KakaoUserInfo;
import com.example.senioron.domain.socialaccount.dto.kakao.response.KakaoWithdrawalLoginResponse;
import com.example.senioron.domain.socialaccount.entity.LoginProvider;
import com.example.senioron.domain.socialaccount.entity.SocialAccount;
import com.example.senioron.domain.socialaccount.repository.SocialAccountRepository;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.entity.UserStatus;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import com.example.senioron.global.jwt.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoWithdrawalLoginService {

    private final KakaoAuthorizationClient kakaoAuthorizationClient;
    private final KakaoUserClient kakaoUserClient;
    private final KakaoAccessTokenValidationService kakaoAccessTokenValidationService;
    private final SocialAccountRepository socialAccountRepository;
    private final JwtUtil jwtUtil;

    @Transactional(readOnly = true)
    public KakaoWithdrawalLoginResponse login(KakaoWithdrawalLoginRequest request) {
        KakaoTokenResponse tokenResponse = exchangeAuthorizationCode(request.getCode());
        String kakaoAccessToken = tokenResponse == null ? null : tokenResponse.getAccessToken();
        if (!StringUtils.hasText(kakaoAccessToken)) {
            throw new BusinessException(ErrorCode.INVALID_SOCIAL_TOKEN);
        }

        kakaoAccessTokenValidationService.validate(kakaoAccessToken);

        KakaoUserInfo kakaoUserInfo = getKakaoUserInfo(kakaoAccessToken);
        if (kakaoUserInfo == null || kakaoUserInfo.getId() == null) {
            throw new BusinessException(ErrorCode.INVALID_SOCIAL_TOKEN);
        }

        SocialAccount socialAccount = socialAccountRepository
                .findByProviderAndProviderId(LoginProvider.KAKAO, String.valueOf(kakaoUserInfo.getId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        User user = socialAccount.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.WITHDRAWN_USER);
        }

        return KakaoWithdrawalLoginResponse.builder()
                .accessToken(jwtUtil.createAccessToken(user))
                .usersId(user.getUsersId())
                .name(user.getName())
                .role(user.getRole())
                .newUser(false)
                .build();
    }

    private KakaoTokenResponse exchangeAuthorizationCode(String code) {
        try {
            return kakaoAuthorizationClient.exchangeAuthorizationCode(code);
        } catch (RestClientResponseException e) {
            throw providerException("token exchange", e);
        } catch (RestClientException e) {
            log.warn("[KAKAO_WITHDRAWAL_LOGIN] token exchange failed: {}", e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private KakaoUserInfo getKakaoUserInfo(String accessToken) {
        try {
            return kakaoUserClient.getUserInfo(accessToken);
        } catch (RestClientResponseException e) {
            throw providerException("user info", e);
        } catch (RestClientException e) {
            log.warn("[KAKAO_WITHDRAWAL_LOGIN] user info lookup failed: {}", e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private BusinessException providerException(String operation, RestClientResponseException exception) {
        log.warn(
                "[KAKAO_WITHDRAWAL_LOGIN] {} rejected by Kakao: httpStatus={}",
                operation,
                exception.getStatusCode()
        );
        if (exception.getStatusCode().is4xxClientError()) {
            return new BusinessException(ErrorCode.INVALID_SOCIAL_TOKEN);
        }
        return new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
    }
}
