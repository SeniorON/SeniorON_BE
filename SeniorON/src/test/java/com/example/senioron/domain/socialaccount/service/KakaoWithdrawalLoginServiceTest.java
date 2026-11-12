package com.example.senioron.domain.socialaccount.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.senioron.domain.socialaccount.client.KakaoAuthorizationClient;
import com.example.senioron.domain.socialaccount.client.KakaoUserClient;
import com.example.senioron.domain.socialaccount.dto.kakao.request.KakaoWithdrawalLoginRequest;
import com.example.senioron.domain.socialaccount.dto.kakao.response.KakaoTokenResponse;
import com.example.senioron.domain.socialaccount.dto.kakao.response.KakaoUserInfo;
import com.example.senioron.domain.socialaccount.dto.kakao.response.KakaoWithdrawalLoginResponse;
import com.example.senioron.domain.socialaccount.entity.LoginProvider;
import com.example.senioron.domain.socialaccount.entity.SocialAccount;
import com.example.senioron.domain.socialaccount.repository.SocialAccountRepository;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import com.example.senioron.global.jwt.JwtUtil;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

class KakaoWithdrawalLoginServiceTest {

    private static final String CODE = "authorization-code";
    private static final String KAKAO_ACCESS_TOKEN = "kakao-access-token";
    private static final String PROVIDER_ID = "12345";

    @Test
    void existingActiveKakaoMemberReceivesAccessTokenWithoutDeviceOrSignupProcessing() {
        Fixture fixture = new Fixture();
        User user = activeUser();
        fixture.stubKakaoIdentity();
        given(fixture.socialAccountRepository.findByProviderAndProviderId(LoginProvider.KAKAO, PROVIDER_ID))
                .willReturn(Optional.of(socialAccount(user)));
        given(fixture.jwtUtil.createAccessToken(user)).willReturn("senioron-jwt");

        KakaoWithdrawalLoginResponse response = fixture.service.login(request());

        assertThat(response.getAccessToken()).isEqualTo("senioron-jwt");
        assertThat(response.getUsersId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("홍길동");
        assertThat(response.getRole()).isEqualTo(Role.CHILD);
        assertThat(response.isNewUser()).isFalse();
        verify(fixture.jwtUtil).createAccessToken(user);
    }

    @Test
    void unregisteredKakaoMemberIsRejectedWithoutCreatingAccount() {
        Fixture fixture = new Fixture();
        fixture.stubKakaoIdentity();
        given(fixture.socialAccountRepository.findByProviderAndProviderId(LoginProvider.KAKAO, PROVIDER_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> fixture.service.login(request()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(fixture.jwtUtil, never()).createAccessToken(any());
    }

    @Test
    void withdrawnKakaoMemberIsRejected() {
        Fixture fixture = new Fixture();
        User withdrawnUser = activeUser();
        withdrawnUser.withdraw("withdrawn", "withdrawn@example.com", "password", LocalDateTime.now());
        fixture.stubKakaoIdentity();
        given(fixture.socialAccountRepository.findByProviderAndProviderId(LoginProvider.KAKAO, PROVIDER_ID))
                .willReturn(Optional.of(socialAccount(withdrawnUser)));

        assertThatThrownBy(() -> fixture.service.login(request()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.WITHDRAWN_USER);

        verify(fixture.jwtUtil, never()).createAccessToken(any());
    }

    @Test
    void invalidOrExpiredAuthorizationCodeIsReportedAsInvalidSocialToken() {
        Fixture fixture = new Fixture();
        given(fixture.authorizationClient.exchangeAuthorizationCode(CODE))
                .willThrow(HttpClientErrorException.create(
                        HttpStatus.BAD_REQUEST,
                        "invalid_grant",
                        HttpHeaders.EMPTY,
                        new byte[0],
                        StandardCharsets.UTF_8
                ));

        assertThatThrownBy(() -> fixture.service.login(request()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.INVALID_SOCIAL_TOKEN);

        verify(fixture.kakaoUserClient, never()).getUserInfo(any());
    }

    @Test
    void unavailableKakaoTokenExchangeIsReportedAsServerError() {
        Fixture fixture = new Fixture();
        given(fixture.authorizationClient.exchangeAuthorizationCode(CODE))
                .willThrow(new ResourceAccessException("Kakao token endpoint timeout"));

        assertThatThrownBy(() -> fixture.service.login(request()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    private static KakaoWithdrawalLoginRequest request() {
        KakaoWithdrawalLoginRequest request = new KakaoWithdrawalLoginRequest();
        ReflectionTestUtils.setField(request, "code", CODE);
        return request;
    }

    private static User activeUser() {
        return User.builder()
                .usersId(1L)
                .name("홍길동")
                .role(Role.CHILD)
                .build();
    }

    private static SocialAccount socialAccount(User user) {
        return SocialAccount.builder()
                .user(user)
                .provider(LoginProvider.KAKAO)
                .providerId(PROVIDER_ID)
                .build();
    }

    private static KakaoUserInfo kakaoUserInfo() {
        KakaoUserInfo userInfo = new KakaoUserInfo();
        ReflectionTestUtils.setField(userInfo, "id", Long.valueOf(PROVIDER_ID));
        return userInfo;
    }

    private static KakaoTokenResponse tokenResponse() {
        KakaoTokenResponse response = new KakaoTokenResponse();
        ReflectionTestUtils.setField(response, "accessToken", KAKAO_ACCESS_TOKEN);
        return response;
    }

    private static class Fixture {
        private final KakaoAuthorizationClient authorizationClient = mock(KakaoAuthorizationClient.class);
        private final KakaoUserClient kakaoUserClient = mock(KakaoUserClient.class);
        private final KakaoAccessTokenValidationService validationService = mock(KakaoAccessTokenValidationService.class);
        private final SocialAccountRepository socialAccountRepository = mock(SocialAccountRepository.class);
        private final JwtUtil jwtUtil = mock(JwtUtil.class);
        private final KakaoWithdrawalLoginService service = new KakaoWithdrawalLoginService(
                authorizationClient,
                kakaoUserClient,
                validationService,
                socialAccountRepository,
                jwtUtil
        );

        private void stubKakaoIdentity() {
            given(authorizationClient.exchangeAuthorizationCode(CODE)).willReturn(tokenResponse());
            given(kakaoUserClient.getUserInfo(KAKAO_ACCESS_TOKEN)).willReturn(kakaoUserInfo());
        }
    }
}
