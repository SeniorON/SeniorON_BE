package com.example.senioron.domain.socialaccount.service;

import com.example.senioron.domain.socialaccount.client.KakaoAccessTokenInfoClient;
import com.example.senioron.domain.socialaccount.dto.kakao.response.KakaoAccessTokenInfo;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoAccessTokenValidationService {

    private final KakaoAccessTokenInfoClient kakaoAccessTokenInfoClient;

    @Value("${kakao.app-id}")
    private Long expectedAppId;

    /**
     * 토큰이 유효하고 현재 서비스에서 사용하는 카카오 앱이 발급한 토큰인지 검증합니다.
     */
    public void validate(String kakaoAccessToken) {
        KakaoAccessTokenInfo accessTokenInfo = getAccessTokenInfo(kakaoAccessToken);

        if (accessTokenInfo == null
                || accessTokenInfo.getAppId() == null
                || !expectedAppId.equals(accessTokenInfo.getAppId())) {
            log.warn(
                    "[KAKAO_TOKEN_VALIDATE] rejected because app id does not match: actualAppId={}",
                    accessTokenInfo == null ? null : accessTokenInfo.getAppId()
            );
            throw new BusinessException(ErrorCode.INVALID_SOCIAL_TOKEN);
        }
    }

    private KakaoAccessTokenInfo getAccessTokenInfo(String kakaoAccessToken) {
        try {
            return kakaoAccessTokenInfoClient.getAccessTokenInfo(kakaoAccessToken);
        } catch (RestClientResponseException e) {
            log.warn(
                    "[KAKAO_TOKEN_VALIDATE] access token info request failed: httpStatus={}",
                    e.getStatusCode()
            );
            if (e.getStatusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED)
                    || e.getStatusCode().isSameCodeAs(HttpStatus.FORBIDDEN)) {
                throw new BusinessException(ErrorCode.INVALID_SOCIAL_TOKEN);
            }
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        } catch (RestClientException e) {
            log.warn(
                    "[KAKAO_TOKEN_VALIDATE] access token info request failed: {}",
                    e.getClass().getSimpleName()
            );
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
