package com.example.senioron.domain.socialaccount.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.senioron.domain.device.service.DeviceService;
import com.example.senioron.domain.inactivity.service.InactivitySettingService;
import com.example.senioron.domain.socialaccount.dto.kakao.response.KakaoUserInfo;
import com.example.senioron.domain.socialaccount.dto.request.SocialSignupRequest;
import com.example.senioron.domain.socialaccount.dto.response.SocialSignupResponse;
import com.example.senioron.domain.socialaccount.entity.LoginProvider;
import com.example.senioron.domain.socialaccount.entity.SocialAccount;
import com.example.senioron.domain.socialaccount.repository.SocialAccountRepository;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.entity.UserStatus;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.domain.user.service.RefreshTokenService;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import com.example.senioron.global.jwt.JwtUtil;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

class SocialSignupServiceTest {

    private static final String SOCIAL_TOKEN = "social-token";
    private static final String KAKAO_PROVIDER_ID = "12345";
    private static final String GOOGLE_PROVIDER_ID = "google-uid";
    private static final String EMAIL = "social@example.com";
    private static final String NAME = "홍길동";
    private static final String ACCESS_TOKEN = "access-token";
    private static final String REFRESH_TOKEN = "refresh-token";
    private static final String FCM_TOKEN = "fcm-token";
    private static final String DEVICE_IDENTIFIER = "device-1";

    private final KakaoLoginService kakaoLoginService = mock(KakaoLoginService.class);
    private final FirebaseIdTokenVerifier firebaseIdTokenVerifier = mock(FirebaseIdTokenVerifier.class);
    private final SocialAccountRepository socialAccountRepository = mock(SocialAccountRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final InactivitySettingService inactivitySettingService = mock(InactivitySettingService.class);
    private final DeviceService deviceService = mock(DeviceService.class);

    private SocialSignupService socialSignupService;

    @BeforeEach
    void setUp() {
        socialSignupService = new SocialSignupService(
                kakaoLoginService,
                firebaseIdTokenVerifier,
                socialAccountRepository,
                userRepository,
                refreshTokenService,
                jwtUtil,
                inactivitySettingService,
                deviceService
        );

        given(jwtUtil.createAccessToken(any(User.class))).willReturn(ACCESS_TOKEN);
        given(jwtUtil.createRefreshToken(any(User.class))).willReturn(REFRESH_TOKEN);
        given(userRepository.saveAndFlush(any(User.class))).willAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "usersId", 1L);
            return user;
        });
        given(socialAccountRepository.saveAndFlush(any(SocialAccount.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void kakaoSocialSignupSucceeds() {
        given(kakaoLoginService.getUserInfo(SOCIAL_TOKEN)).willReturn(createKakaoUserInfo());
        given(socialAccountRepository.existsByProviderAndProviderId(LoginProvider.KAKAO, KAKAO_PROVIDER_ID))
                .willReturn(false);

        SocialSignupResponse response = socialSignupService.signup(createRequest(LoginProvider.KAKAO));

        assertThat(response.getAccessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(response.getRefreshToken()).isEqualTo(REFRESH_TOKEN);
        assertThat(response.getUsersId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo(NAME);
        assertThat(response.isNewUser()).isFalse();
        verify(userRepository).saveAndFlush(any(User.class));
        verify(socialAccountRepository).saveAndFlush(any(SocialAccount.class));
        verify(deviceService, never()).registerToken(any(User.class), any(), any());
        verify(refreshTokenService).saveOrRotate(any(User.class), isNull(), eq(REFRESH_TOKEN));
    }

    @Test
    void googleSocialSignupSucceeds() {
        given(firebaseIdTokenVerifier.verify(SOCIAL_TOKEN))
                .willReturn(new VerifiedFirebaseUser(GOOGLE_PROVIDER_ID, EMAIL, "구글사용자"));
        given(socialAccountRepository.existsByProviderAndProviderId(LoginProvider.GOOGLE, GOOGLE_PROVIDER_ID))
                .willReturn(false);

        SocialSignupResponse response = socialSignupService.signup(createRequest(LoginProvider.GOOGLE));

        assertThat(response.getAccessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(response.getRefreshToken()).isEqualTo(REFRESH_TOKEN);
        assertThat(response.getUsersId()).isEqualTo(1L);
        assertThat(response.isNewUser()).isFalse();
        verify(userRepository).saveAndFlush(any(User.class));
        verify(socialAccountRepository).saveAndFlush(any(SocialAccount.class));
        verify(refreshTokenService).saveOrRotate(any(User.class), isNull(), eq(REFRESH_TOKEN));
    }

    @Test
    void googleSocialSignupRegistersDeviceAndBindsRefreshTokenWhenDeviceInfoProvided() {
        given(firebaseIdTokenVerifier.verify(SOCIAL_TOKEN))
                .willReturn(new VerifiedFirebaseUser(GOOGLE_PROVIDER_ID, EMAIL, "구글사용자"));
        given(socialAccountRepository.existsByProviderAndProviderId(LoginProvider.GOOGLE, GOOGLE_PROVIDER_ID))
                .willReturn(false);

        SocialSignupResponse response =
                socialSignupService.signup(createRequest(LoginProvider.GOOGLE, FCM_TOKEN, DEVICE_IDENTIFIER));

        assertThat(response.getAccessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(response.getRefreshToken()).isEqualTo(REFRESH_TOKEN);
        verify(deviceService).registerToken(any(User.class), eq(FCM_TOKEN), eq(DEVICE_IDENTIFIER));
        verify(refreshTokenService).saveOrRotate(any(User.class), eq(DEVICE_IDENTIFIER), eq(REFRESH_TOKEN));
    }

    @Test
    void socialSignupFailsWhenSocialAccountAlreadyExists() {
        given(kakaoLoginService.getUserInfo(SOCIAL_TOKEN)).willReturn(createKakaoUserInfo());
        given(socialAccountRepository.existsByProviderAndProviderId(LoginProvider.KAKAO, KAKAO_PROVIDER_ID))
                .willReturn(true);

        assertThatThrownBy(() -> socialSignupService.signup(createRequest(LoginProvider.KAKAO)))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.SOCIAL_ACCOUNT_ALREADY_EXISTS);

        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void socialSignupFailsWhenRequiredTermsAreNotAgreed() {
        given(kakaoLoginService.getUserInfo(SOCIAL_TOKEN)).willReturn(createKakaoUserInfo());

        assertThatThrownBy(() -> socialSignupService.signup(createRequest(
                LoginProvider.KAKAO,
                LocalDate.now().minusYears(20),
                false,
                true,
                true
        )))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.REQUIRED_TERMS_NOT_AGREED);
    }

    @Test
    void socialSignupFailsWhenUserIsUnder14() {
        given(kakaoLoginService.getUserInfo(SOCIAL_TOKEN)).willReturn(createKakaoUserInfo());

        assertThatThrownBy(() -> socialSignupService.signup(createRequest(
                LoginProvider.KAKAO,
                LocalDate.now().minusYears(13),
                true,
                true,
                true
        )))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.UNDER_AGE_14);
    }

    @Test
    void socialSignupFailsWhenBirthIsFutureDate() {
        given(kakaoLoginService.getUserInfo(SOCIAL_TOKEN)).willReturn(createKakaoUserInfo());

        assertThatThrownBy(() -> socialSignupService.signup(createRequest(
                LoginProvider.KAKAO,
                LocalDate.now().plusDays(1),
                true,
                true,
                true
        )))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.INVALID_BIRTH_DATE);
    }

    @Test
    void socialSignupStopsWhenUserCreationFails() {
        given(kakaoLoginService.getUserInfo(SOCIAL_TOKEN)).willReturn(createKakaoUserInfo());
        given(userRepository.saveAndFlush(any(User.class)))
                .willThrow(new DataIntegrityViolationException("duplicate email"));

        assertThatThrownBy(() -> socialSignupService.signup(createRequest(LoginProvider.KAKAO)))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.DUPLICATE_EMAIL);

        verify(socialAccountRepository, never()).saveAndFlush(any(SocialAccount.class));
        verify(refreshTokenService, never()).saveOrRotate(any(User.class), any(), any());
    }

    @Test
    void socialSignupFailsWhenSocialAccountCreationFails() {
        given(kakaoLoginService.getUserInfo(SOCIAL_TOKEN)).willReturn(createKakaoUserInfo());
        given(socialAccountRepository.saveAndFlush(any(SocialAccount.class)))
                .willThrow(new DataIntegrityViolationException("duplicate social account"));

        assertThatThrownBy(() -> socialSignupService.signup(createRequest(LoginProvider.KAKAO)))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.SOCIAL_ACCOUNT_ALREADY_EXISTS);

        verify(refreshTokenService, never()).saveOrRotate(any(User.class), any(), any());
    }

    private SocialSignupRequest createRequest(LoginProvider provider) {
        return createRequest(provider, null, null);
    }

    private SocialSignupRequest createRequest(LoginProvider provider, String fcmToken, String deviceIdentifier) {
        return createRequest(
                provider,
                LocalDate.now().minusYears(20),
                true,
                true,
                true,
                fcmToken,
                deviceIdentifier
        );
    }

    private SocialSignupRequest createRequest(
            LoginProvider provider,
            LocalDate birth,
            boolean serviceTermsAgreed,
            boolean privacyPolicyAgreed,
            boolean ageOver14Agreed
    ) {
        return createRequest(provider, birth, serviceTermsAgreed, privacyPolicyAgreed, ageOver14Agreed, null, null);
    }

    private SocialSignupRequest createRequest(
            LoginProvider provider,
            LocalDate birth,
            boolean serviceTermsAgreed,
            boolean privacyPolicyAgreed,
            boolean ageOver14Agreed,
            String fcmToken,
            String deviceIdentifier
    ) {
        SocialSignupRequest request = new SocialSignupRequest();
        ReflectionTestUtils.setField(request, "provider", provider);
        ReflectionTestUtils.setField(request, "socialToken", SOCIAL_TOKEN);
        ReflectionTestUtils.setField(request, "name", NAME);
        ReflectionTestUtils.setField(request, "birth", birth);
        ReflectionTestUtils.setField(request, "serviceTermsAgreed", serviceTermsAgreed);
        ReflectionTestUtils.setField(request, "privacyPolicyAgreed", privacyPolicyAgreed);
        ReflectionTestUtils.setField(request, "ageOver14Agreed", ageOver14Agreed);
        ReflectionTestUtils.setField(request, "marketingAgreed", false);
        ReflectionTestUtils.setField(request, "fcmToken", fcmToken);
        ReflectionTestUtils.setField(request, "deviceIdentifier", deviceIdentifier);
        return request;
    }

    private KakaoUserInfo createKakaoUserInfo() {
        KakaoUserInfo kakaoUserInfo = new KakaoUserInfo();
        KakaoUserInfo.KakaoAccount kakaoAccount = new KakaoUserInfo.KakaoAccount();
        ReflectionTestUtils.setField(kakaoUserInfo, "id", Long.valueOf(KAKAO_PROVIDER_ID));
        ReflectionTestUtils.setField(kakaoAccount, "email", EMAIL);
        ReflectionTestUtils.setField(kakaoUserInfo, "kakaoAccount", kakaoAccount);
        return kakaoUserInfo;
    }
}
