package com.example.senioron.domain.socialaccount.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.example.senioron.domain.socialaccount.client.KakaoAccessTokenInfoClient;
import com.example.senioron.domain.socialaccount.dto.kakao.response.KakaoAccessTokenInfo;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

class KakaoAccessTokenValidationServiceTest {

    private static final String ACCESS_TOKEN = "kakao-access-token";
    private static final long EXPECTED_APP_ID = 12345L;

    private final KakaoAccessTokenInfoClient kakaoAccessTokenInfoClient =
            mock(KakaoAccessTokenInfoClient.class);
    private final KakaoAccessTokenValidationService service =
            new KakaoAccessTokenValidationService(kakaoAccessTokenInfoClient);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "expectedAppId", EXPECTED_APP_ID);
    }

    @Test
    void validatesTokenIssuedByExpectedKakaoApp() {
        given(kakaoAccessTokenInfoClient.getAccessTokenInfo(ACCESS_TOKEN))
                .willReturn(accessTokenInfo(EXPECTED_APP_ID));

        service.validate(ACCESS_TOKEN);
    }

    @Test
    void rejectsTokenIssuedByDifferentKakaoApp() {
        given(kakaoAccessTokenInfoClient.getAccessTokenInfo(ACCESS_TOKEN))
                .willReturn(accessTokenInfo(98765L));

        assertInvalidSocialToken();
    }

    @Test
    void rejectsInvalidOrExpiredToken() {
        given(kakaoAccessTokenInfoClient.getAccessTokenInfo(ACCESS_TOKEN))
                .willThrow(HttpClientErrorException.create(
                        HttpStatus.UNAUTHORIZED,
                        "Unauthorized",
                        HttpHeaders.EMPTY,
                        new byte[0],
                        StandardCharsets.UTF_8
                ));

        assertInvalidSocialToken();
    }

    @Test
    void mapsKakaoBadRequestToInternalServerError() {
        given(kakaoAccessTokenInfoClient.getAccessTokenInfo(ACCESS_TOKEN))
                .willThrow(HttpClientErrorException.create(
                        HttpStatus.BAD_REQUEST,
                        "Bad Request",
                        HttpHeaders.EMPTY,
                        new byte[0],
                        StandardCharsets.UTF_8
                ));

        assertInternalServerError();
    }

    @Test
    void mapsKakaoServerErrorToInternalServerError() {
        given(kakaoAccessTokenInfoClient.getAccessTokenInfo(ACCESS_TOKEN))
                .willThrow(new HttpServerErrorException(HttpStatus.BAD_GATEWAY));

        assertInternalServerError();
    }

    @Test
    void mapsKakaoConnectionFailureToInternalServerError() {
        given(kakaoAccessTokenInfoClient.getAccessTokenInfo(ACCESS_TOKEN))
                .willThrow(new ResourceAccessException("Kakao timeout"));

        assertInternalServerError();
    }

    private KakaoAccessTokenInfo accessTokenInfo(long appId) {
        KakaoAccessTokenInfo accessTokenInfo = new KakaoAccessTokenInfo();
        ReflectionTestUtils.setField(accessTokenInfo, "appId", appId);
        return accessTokenInfo;
    }

    private void assertInvalidSocialToken() {
        assertThatThrownBy(() -> service.validate(ACCESS_TOKEN))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.INVALID_SOCIAL_TOKEN);
    }

    private void assertInternalServerError() {
        assertThatThrownBy(() -> service.validate(ACCESS_TOKEN))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
    }
}
