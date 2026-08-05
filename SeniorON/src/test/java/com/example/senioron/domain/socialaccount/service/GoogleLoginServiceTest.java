package com.example.senioron.domain.socialaccount.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.senioron.domain.device.service.DeviceService;
import com.example.senioron.domain.socialaccount.dto.google.request.GoogleLoginRequest;
import com.example.senioron.domain.socialaccount.dto.google.response.GoogleLoginResponse;
import com.example.senioron.domain.socialaccount.entity.LoginProvider;
import com.example.senioron.domain.socialaccount.entity.SocialAccount;
import com.example.senioron.domain.socialaccount.repository.SocialAccountRepository;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.service.RefreshTokenService;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import com.example.senioron.global.jwt.JwtUtil;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class GoogleLoginServiceTest {

    private static final String FIREBASE_ID_TOKEN = "firebase-id-token";
    private static final String PROVIDER_ID = "google-uid";
    private static final String EMAIL = "google@example.com";
    private static final String NAME = "구글사용자";
    private static final String ACCESS_TOKEN = "senioron-jwt";
    private static final String REFRESH_TOKEN = "senioron-refresh-jwt";
    private static final String FCM_TOKEN = "fcm-token";
    private static final String DEVICE_IDENTIFIER = "device-1";

    private final FirebaseIdTokenVerifier firebaseIdTokenVerifier = mock(FirebaseIdTokenVerifier.class);
    private final SocialAccountRepository socialAccountRepository = mock(SocialAccountRepository.class);
    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private final DeviceService deviceService = mock(DeviceService.class);

    private GoogleLoginService googleLoginService;

    @BeforeEach
    void setUp() {
        googleLoginService = new GoogleLoginService(
                firebaseIdTokenVerifier,
                socialAccountRepository,
                jwtUtil,
                refreshTokenService,
                deviceService
        );
    }

    @Test
    void googleLoginIssuesJwtForExistingAccount() {
        User user = createUser();
        SocialAccount socialAccount = createSocialAccount(user);
        given(firebaseIdTokenVerifier.verify(FIREBASE_ID_TOKEN))
                .willReturn(new VerifiedFirebaseUser(PROVIDER_ID, EMAIL, NAME));
        given(socialAccountRepository.findByProviderAndProviderId(LoginProvider.GOOGLE, PROVIDER_ID))
                .willReturn(Optional.of(socialAccount));
        given(jwtUtil.createAccessToken(user)).willReturn(ACCESS_TOKEN);
        given(jwtUtil.createRefreshToken(user)).willReturn(REFRESH_TOKEN);

        GoogleLoginResponse response = googleLoginService.googleLogin(createRequest());

        assertThat(response.getAccessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(response.getRefreshToken()).isEqualTo(REFRESH_TOKEN);
        assertThat(response.getUsersId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo(NAME);
        assertThat(response.getRole()).isEqualTo(Role.CHILD);
        assertThat(response.isNewUser()).isFalse();
        verify(refreshTokenService).saveOrRotate(user, null, REFRESH_TOKEN);
    }

    @Test
    void googleLoginReturnsNewUserWithoutCreatingAccountOrIssuingJwt() {
        given(firebaseIdTokenVerifier.verify(FIREBASE_ID_TOKEN))
                .willReturn(new VerifiedFirebaseUser(PROVIDER_ID, EMAIL, NAME));
        given(socialAccountRepository.findByProviderAndProviderId(LoginProvider.GOOGLE, PROVIDER_ID))
                .willReturn(Optional.empty());

        GoogleLoginResponse response = googleLoginService.googleLogin(createRequest());

        assertThat(response.getAccessToken()).isNull();
        assertThat(response.getRefreshToken()).isNull();
        assertThat(response.getUsersId()).isNull();
        assertThat(response.getName()).isEqualTo(NAME);
        assertThat(response.getRole()).isNull();
        assertThat(response.isNewUser()).isTrue();
        verify(jwtUtil, never()).createAccessToken(any());
        verify(jwtUtil, never()).createRefreshToken(any());
        verify(refreshTokenService, never()).saveOrRotate(any(), any(), any());
        verify(deviceService, never()).registerToken(any(), any(), any());
    }

    @Test
    void googleLoginRegistersFcmTokenWhenProvided() {
        User user = createUser();
        SocialAccount socialAccount = createSocialAccount(user);
        given(firebaseIdTokenVerifier.verify(FIREBASE_ID_TOKEN))
                .willReturn(new VerifiedFirebaseUser(PROVIDER_ID, EMAIL, NAME));
        given(socialAccountRepository.findByProviderAndProviderId(LoginProvider.GOOGLE, PROVIDER_ID))
                .willReturn(Optional.of(socialAccount));
        given(jwtUtil.createAccessToken(user)).willReturn(ACCESS_TOKEN);
        given(jwtUtil.createRefreshToken(user)).willReturn(REFRESH_TOKEN);

        googleLoginService.googleLogin(createRequest(FCM_TOKEN, DEVICE_IDENTIFIER));

        verify(deviceService).registerToken(user, FCM_TOKEN, DEVICE_IDENTIFIER);
        verify(refreshTokenService).saveOrRotate(user, DEVICE_IDENTIFIER, REFRESH_TOKEN);
    }

    @Test
    void googleLoginRejectsInvalidFirebaseToken() {
        given(firebaseIdTokenVerifier.verify(FIREBASE_ID_TOKEN))
                .willThrow(new BusinessException(ErrorCode.INVALID_FIREBASE_ID_TOKEN));

        assertThatThrownBy(() -> googleLoginService.googleLogin(createRequest()))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.INVALID_FIREBASE_ID_TOKEN);
    }

    @Test
    void googleLoginRejectsMissingRequiredFirebaseUserInfo() {
        given(firebaseIdTokenVerifier.verify(anyString()))
                .willReturn(new VerifiedFirebaseUser(PROVIDER_ID, EMAIL, " "));

        assertThatThrownBy(() -> googleLoginService.googleLogin(createRequest()))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.INVALID_FIREBASE_ID_TOKEN);
    }

    private GoogleLoginRequest createRequest() {
        return createRequest(null, null);
    }

    private GoogleLoginRequest createRequest(String fcmToken, String deviceIdentifier) {
        GoogleLoginRequest request = new GoogleLoginRequest();
        ReflectionTestUtils.setField(request, "firebaseIdToken", FIREBASE_ID_TOKEN);
        ReflectionTestUtils.setField(request, "fcmToken", fcmToken);
        ReflectionTestUtils.setField(request, "deviceIdentifier", deviceIdentifier);
        return request;
    }

    private User createUser() {
        return User.builder()
                .usersId(1L)
                .email(EMAIL)
                .name(NAME)
                .role(Role.CHILD)
                .build();
    }

    private SocialAccount createSocialAccount(User user) {
        return SocialAccount.builder()
                .socialAccountId(10L)
                .user(user)
                .provider(LoginProvider.GOOGLE)
                .providerId(PROVIDER_ID)
                .build();
    }
}
