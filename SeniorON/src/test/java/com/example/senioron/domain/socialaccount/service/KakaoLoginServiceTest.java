package com.example.senioron.domain.socialaccount.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.senioron.domain.device.service.DeviceService;
import com.example.senioron.domain.socialaccount.client.KakaoUserClient;
import com.example.senioron.domain.socialaccount.dto.kakao.request.KakaoLoginRequest;
import com.example.senioron.domain.socialaccount.dto.kakao.response.KakaoLoginResponse;
import com.example.senioron.domain.socialaccount.dto.kakao.response.KakaoUserInfo;
import com.example.senioron.domain.socialaccount.entity.LoginProvider;
import com.example.senioron.domain.socialaccount.entity.SocialAccount;
import com.example.senioron.domain.socialaccount.repository.SocialAccountRepository;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.domain.user.service.RefreshTokenService;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import com.example.senioron.global.jwt.JwtUtil;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class KakaoLoginServiceTest {

    private static final String KAKAO_ACCESS_TOKEN = "kakao-access-token";
    private static final String KAKAO_PROVIDER_ID = "12345";
    private static final String ACCESS_TOKEN = "access-token";
    private static final String REFRESH_TOKEN = "refresh-token";
    private static final String FCM_TOKEN = "fcm-token";
    private static final String DEVICE_IDENTIFIER = "device-1";

    @Test
    void kakaoLoginFetchesKakaoUserInfoOutsideTransactionService() {
        KakaoUserClient kakaoUserClient = mock(KakaoUserClient.class);
        KakaoLoginTransactionService transactionService = mock(KakaoLoginTransactionService.class);
        KakaoLoginService kakaoLoginService = new KakaoLoginService(kakaoUserClient, transactionService);
        KakaoLoginRequest request = createRequest();
        KakaoUserInfo kakaoUserInfo = createKakaoUserInfo();
        KakaoLoginResponse expected = KakaoLoginResponse.builder()
                .providerId(KAKAO_PROVIDER_ID)
                .newUser(true)
                .build();

        given(kakaoUserClient.getUserInfo(KAKAO_ACCESS_TOKEN)).willReturn(kakaoUserInfo);
        given(transactionService.login(request, kakaoUserInfo)).willReturn(expected);

        KakaoLoginResponse response = kakaoLoginService.kakaoLogin(request);

        assertThat(response).isSameAs(expected);
        verify(kakaoUserClient).getUserInfo(KAKAO_ACCESS_TOKEN);
        verify(transactionService).login(request, kakaoUserInfo);
    }

    @Test
    void kakaoLoginDoesNotCallTransactionServiceWhenRequiredKakaoInfoIsMissing() {
        KakaoUserClient kakaoUserClient = mock(KakaoUserClient.class);
        KakaoLoginTransactionService transactionService = mock(KakaoLoginTransactionService.class);
        KakaoLoginService kakaoLoginService = new KakaoLoginService(kakaoUserClient, transactionService);
        KakaoLoginRequest request = createRequest();

        given(kakaoUserClient.getUserInfo(KAKAO_ACCESS_TOKEN)).willReturn(new KakaoUserInfo());

        assertThatThrownBy(() -> kakaoLoginService.kakaoLogin(request))
                .isInstanceOf(IllegalArgumentException.class);

        verify(transactionService, never()).login(any(), any());
    }

    @Test
    void kakaoLoginTransactionServiceReturnsExistingUserTokens() {
        SocialAccountRepository socialAccountRepository = mock(SocialAccountRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        JwtUtil jwtUtil = mock(JwtUtil.class);
        RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
        DeviceService deviceService = mock(DeviceService.class);
        KakaoLoginTransactionService transactionService = new KakaoLoginTransactionService(
                socialAccountRepository,
                userRepository,
                jwtUtil,
                refreshTokenService,
                deviceService
        );
        KakaoLoginRequest request = createRequest();
        User user = createUser();
        SocialAccount socialAccount = SocialAccount.builder()
                .user(user)
                .provider(LoginProvider.KAKAO)
                .providerId(KAKAO_PROVIDER_ID)
                .build();

        given(socialAccountRepository.findByProviderAndProviderId(LoginProvider.KAKAO, KAKAO_PROVIDER_ID))
                .willReturn(Optional.of(socialAccount));
        given(jwtUtil.createAccessToken(user)).willReturn(ACCESS_TOKEN);
        given(jwtUtil.createRefreshToken(user)).willReturn(REFRESH_TOKEN);

        KakaoLoginResponse response = transactionService.login(request, createKakaoUserInfo());

        assertThat(response.getAccessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(response.getRefreshToken()).isEqualTo(REFRESH_TOKEN);
        assertThat(response.getUsersId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("홍길동");
        assertThat(response.getRole()).isEqualTo(Role.CHILD);
        assertThat(response.isNewUser()).isFalse();
        verify(deviceService).registerDevice(user, DEVICE_IDENTIFIER);
        verify(deviceService).updateFcmToken(user, FCM_TOKEN, DEVICE_IDENTIFIER);
        verify(refreshTokenService).saveOrRotate(user, DEVICE_IDENTIFIER, REFRESH_TOKEN);
    }

    @Test
    void kakaoLoginTransactionServiceRegistersDeviceWithoutFcmToken() {
        SocialAccountRepository socialAccountRepository = mock(SocialAccountRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        JwtUtil jwtUtil = mock(JwtUtil.class);
        RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
        DeviceService deviceService = mock(DeviceService.class);
        KakaoLoginTransactionService transactionService = new KakaoLoginTransactionService(
                socialAccountRepository,
                userRepository,
                jwtUtil,
                refreshTokenService,
                deviceService
        );
        KakaoLoginRequest request = createRequest(null, DEVICE_IDENTIFIER);
        User user = createUser();
        SocialAccount socialAccount = SocialAccount.builder()
                .user(user)
                .provider(LoginProvider.KAKAO)
                .providerId(KAKAO_PROVIDER_ID)
                .build();

        given(socialAccountRepository.findByProviderAndProviderId(LoginProvider.KAKAO, KAKAO_PROVIDER_ID))
                .willReturn(Optional.of(socialAccount));
        given(jwtUtil.createAccessToken(user)).willReturn(ACCESS_TOKEN);
        given(jwtUtil.createRefreshToken(user)).willReturn(REFRESH_TOKEN);

        transactionService.login(request, createKakaoUserInfo());

        verify(deviceService).registerDevice(user, DEVICE_IDENTIFIER);
        verify(deviceService, never()).updateFcmToken(any(), any(), any());
        verify(refreshTokenService).saveOrRotate(user, DEVICE_IDENTIFIER, REFRESH_TOKEN);
    }

    @Test
    void kakaoLoginTransactionServiceReturnsNewUserResponseWhenSocialAccountMissing() {
        SocialAccountRepository socialAccountRepository = mock(SocialAccountRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        KakaoLoginTransactionService transactionService = new KakaoLoginTransactionService(
                socialAccountRepository,
                userRepository,
                mock(JwtUtil.class),
                mock(RefreshTokenService.class),
                mock(DeviceService.class)
        );

        given(socialAccountRepository.findByProviderAndProviderId(LoginProvider.KAKAO, KAKAO_PROVIDER_ID))
                .willReturn(Optional.empty());

        KakaoLoginResponse response = transactionService.login(createRequest(), createKakaoUserInfo());

        assertThat(response.getAccessToken()).isNull();
        assertThat(response.getUsersId()).isNull();
        assertThat(response.getName()).isEqualTo("카카오사용자");
        assertThat(response.getRole()).isNull();
        assertThat(response.getProviderId()).isEqualTo(KAKAO_PROVIDER_ID);
        assertThat(response.isNewUser()).isTrue();
    }

    @Test
    void kakaoLoginLinksVerifiedEmailToExistingGeneralAccount() {
        SocialAccountRepository socialAccountRepository = mock(SocialAccountRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        JwtUtil jwtUtil = mock(JwtUtil.class);
        RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
        DeviceService deviceService = mock(DeviceService.class);
        KakaoLoginTransactionService transactionService = new KakaoLoginTransactionService(
                socialAccountRepository,
                userRepository,
                jwtUtil,
                refreshTokenService,
                deviceService
        );
        User user = createGeneralUser();

        given(socialAccountRepository.findByProviderAndProviderId(LoginProvider.KAKAO, KAKAO_PROVIDER_ID))
                .willReturn(Optional.empty());
        given(userRepository.findGeneralAccountByEmailForUpdate("user@example.com"))
                .willReturn(Optional.of(user));
        given(socialAccountRepository.findByUserAndProvider(user, LoginProvider.KAKAO))
                .willReturn(Optional.empty());
        given(socialAccountRepository.saveAndFlush(any(SocialAccount.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(jwtUtil.createAccessToken(user)).willReturn(ACCESS_TOKEN);
        given(jwtUtil.createRefreshToken(user)).willReturn(REFRESH_TOKEN);

        KakaoLoginResponse response = transactionService.login(
                createRequest(),
                createVerifiedKakaoUserInfo()
        );

        assertThat(response.getUsersId()).isEqualTo(user.getUsersId());
        assertThat(response.getAccessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(response.isNewUser()).isFalse();
        ArgumentCaptor<SocialAccount> socialAccountCaptor =
                ArgumentCaptor.forClass(SocialAccount.class);
        verify(socialAccountRepository).saveAndFlush(socialAccountCaptor.capture());
        assertThat(socialAccountCaptor.getValue().getUser()).isSameAs(user);
        assertThat(socialAccountCaptor.getValue().getProvider())
                .isEqualTo(LoginProvider.KAKAO);
        assertThat(socialAccountCaptor.getValue().getProviderId()).isEqualTo(KAKAO_PROVIDER_ID);
    }

    @Test
    void kakaoLoginDoesNotLinkUnverifiedEmailToGeneralAccount() {
        SocialAccountRepository socialAccountRepository = mock(SocialAccountRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        KakaoLoginTransactionService transactionService = new KakaoLoginTransactionService(
                socialAccountRepository,
                userRepository,
                mock(JwtUtil.class),
                mock(RefreshTokenService.class),
                mock(DeviceService.class)
        );
        given(socialAccountRepository.findByProviderAndProviderId(LoginProvider.KAKAO, KAKAO_PROVIDER_ID))
                .willReturn(Optional.empty());

        KakaoLoginResponse response = transactionService.login(
                createRequest(),
                createKakaoUserInfo()
        );

        assertThat(response.isNewUser()).isTrue();
        verify(userRepository, never()).findGeneralAccountByEmailForUpdate(any());
        verify(socialAccountRepository, never()).saveAndFlush(any(SocialAccount.class));
    }

    @Test
    void kakaoLoginTransactionServiceRejectsWithdrawnUser() {
        SocialAccountRepository socialAccountRepository = mock(SocialAccountRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        KakaoLoginTransactionService transactionService = new KakaoLoginTransactionService(
                socialAccountRepository,
                userRepository,
                mock(JwtUtil.class),
                mock(RefreshTokenService.class),
                mock(DeviceService.class)
        );
        User user = createUser();
        user.withdraw("withdrawn", "withdrawn@example.com", "password", java.time.LocalDateTime.now());
        SocialAccount socialAccount = SocialAccount.builder()
                .user(user)
                .provider(LoginProvider.KAKAO)
                .providerId(KAKAO_PROVIDER_ID)
                .build();

        given(socialAccountRepository.findByProviderAndProviderId(LoginProvider.KAKAO, KAKAO_PROVIDER_ID))
                .willReturn(Optional.of(socialAccount));

        assertThatThrownBy(() -> transactionService.login(createRequest(), createKakaoUserInfo()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.WITHDRAWN_USER);
    }

    private KakaoLoginRequest createRequest() {
        return createRequest(FCM_TOKEN, DEVICE_IDENTIFIER);
    }

    private KakaoLoginRequest createRequest(String fcmToken, String deviceIdentifier) {
        KakaoLoginRequest request = new KakaoLoginRequest();
        ReflectionTestUtils.setField(request, "kakaoAccessToken", KAKAO_ACCESS_TOKEN);
        ReflectionTestUtils.setField(request, "fcmToken", fcmToken);
        ReflectionTestUtils.setField(request, "deviceIdentifier", deviceIdentifier);
        return request;
    }

    private User createUser() {
        return User.builder()
                .usersId(1L)
                .name("홍길동")
                .email("user@example.com")
                .role(Role.CHILD)
                .build();
    }

    private User createGeneralUser() {
        return User.builder()
                .usersId(1L)
                .loginId("general-user")
                .password("encoded-password")
                .name("홍길동")
                .email("user@example.com")
                .role(Role.CHILD)
                .build();
    }

    private KakaoUserInfo createKakaoUserInfo() {
        KakaoUserInfo kakaoUserInfo = new KakaoUserInfo();
        KakaoUserInfo.KakaoAccount kakaoAccount = new KakaoUserInfo.KakaoAccount();
        KakaoUserInfo.Profile profile = new KakaoUserInfo.Profile();
        ReflectionTestUtils.setField(kakaoUserInfo, "id", Long.valueOf(KAKAO_PROVIDER_ID));
        ReflectionTestUtils.setField(kakaoAccount, "profile", profile);
        ReflectionTestUtils.setField(profile, "nickname", "카카오사용자");
        ReflectionTestUtils.setField(kakaoUserInfo, "kakaoAccount", kakaoAccount);
        return kakaoUserInfo;
    }

    private KakaoUserInfo createVerifiedKakaoUserInfo() {
        KakaoUserInfo kakaoUserInfo = createKakaoUserInfo();
        KakaoUserInfo.KakaoAccount kakaoAccount =
                (KakaoUserInfo.KakaoAccount) ReflectionTestUtils.getField(
                        kakaoUserInfo,
                        "kakaoAccount"
                );
        ReflectionTestUtils.setField(kakaoAccount, "email", "user@example.com");
        ReflectionTestUtils.setField(kakaoAccount, "emailValid", true);
        ReflectionTestUtils.setField(kakaoAccount, "emailVerified", true);
        return kakaoUserInfo;
    }
}
