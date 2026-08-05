package com.example.senioron.domain.socialaccount.service;

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
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class SocialSignupService {

    private final KakaoLoginService kakaoLoginService;
    private final FirebaseIdTokenVerifier firebaseIdTokenVerifier;
    private final SocialAccountRepository socialAccountRepository;
    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final JwtUtil jwtUtil;
    private final InactivitySettingService inactivitySettingService;

    public SocialSignupResponse signup(SocialSignupRequest request) {
        SocialTokenInfo socialTokenInfo = verifySocialToken(request);

        if (socialAccountRepository.existsByProviderAndProviderId(
                request.getProvider(),
                socialTokenInfo.providerId()
        )) {
            throw new BusinessException(ErrorCode.SOCIAL_ACCOUNT_ALREADY_EXISTS);
        }

        validateSignupInput(request);

        User user = createUser(request, socialTokenInfo);
        SocialAccount socialAccount = createSocialAccount(request.getProvider(), socialTokenInfo.providerId(), user);
        inactivitySettingService.createDefaultSetting(user);

        String refreshToken = jwtUtil.createRefreshToken(socialAccount.getUser());
        refreshTokenService.saveOrRotate(socialAccount.getUser(), null, refreshToken);
        String accessToken = jwtUtil.createAccessToken(socialAccount.getUser());

        return SocialSignupResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .usersId(user.getUsersId())
                .name(user.getName())
                .role(user.getRole())
                .newUser(false)
                .build();
    }

    private SocialTokenInfo verifySocialToken(SocialSignupRequest request) {
        if (request.getProvider() == LoginProvider.KAKAO) {
            KakaoUserInfo kakaoUserInfo = kakaoLoginService.getUserInfo(request.getSocialToken());
            if (kakaoUserInfo == null || kakaoUserInfo.getId() == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST);
            }

            return new SocialTokenInfo(String.valueOf(kakaoUserInfo.getId()), kakaoUserInfo.getEmail());
        }

        if (request.getProvider() == LoginProvider.GOOGLE) {
            VerifiedFirebaseUser firebaseUser = firebaseIdTokenVerifier.verify(request.getSocialToken());
            if (firebaseUser == null || isBlank(firebaseUser.uid())) {
                throw new BusinessException(ErrorCode.INVALID_FIREBASE_ID_TOKEN);
            }

            return new SocialTokenInfo(firebaseUser.uid(), firebaseUser.email());
        }

        throw new BusinessException(ErrorCode.BAD_REQUEST);
    }

    private void validateSignupInput(SocialSignupRequest request) {
        LocalDate today = LocalDate.now();

        if (request.getBirth().isAfter(today)) {
            throw new BusinessException(ErrorCode.INVALID_BIRTH_DATE);
        }

        if (request.getBirth().plusYears(14).isAfter(today)) {
            throw new BusinessException(ErrorCode.UNDER_AGE_14);
        }

        if (!Boolean.TRUE.equals(request.getServiceTermsAgreed())
                || !Boolean.TRUE.equals(request.getPrivacyPolicyAgreed())
                || !Boolean.TRUE.equals(request.getAgeOver14Agreed())) {
            throw new BusinessException(ErrorCode.REQUIRED_TERMS_NOT_AGREED);
        }
    }

    private User createUser(SocialSignupRequest request, SocialTokenInfo socialTokenInfo) {
        try {
            return userRepository.saveAndFlush(
                    User.builder()
                            .email(blankToNull(socialTokenInfo.email()))
                            .name(request.getName())
                            .birth(request.getBirth())
                            .status(UserStatus.ACTIVE)
                            .serviceTermsAgreed(request.getServiceTermsAgreed())
                            .privacyPolicyAgreed(request.getPrivacyPolicyAgreed())
                            .ageOver14Agreed(request.getAgeOver14Agreed())
                            .marketingAgreed(request.getMarketingAgreed())
                            .build()
            );
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
    }

    private SocialAccount createSocialAccount(LoginProvider provider, String providerId, User user) {
        try {
            return socialAccountRepository.saveAndFlush(
                    SocialAccount.builder()
                            .user(user)
                            .provider(provider)
                            .providerId(providerId)
                            .build()
            );
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.SOCIAL_ACCOUNT_ALREADY_EXISTS);
        }
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record SocialTokenInfo(
            String providerId,
            String email
    ) {
    }
}
