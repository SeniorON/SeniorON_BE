package com.example.senioron.domain.socialaccount.service;

import com.example.senioron.domain.inactivity.service.InactivitySettingService;
import com.example.senioron.domain.device.service.DeviceService;
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
import java.sql.SQLException;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
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
    private final DeviceService deviceService;

    public SocialSignupResponse signup(SocialSignupRequest request) {
        log.info(
                "[SOCIAL_SIGNUP] started provider={} role={} birthPresent={} requiredTermsAllTrue={} "
                        + "marketingAgreedPresent={} fcmTokenPresent={} deviceIdentifierPresent={}",
                request.getProvider(),
                request.getRole(),
                request.getBirth() != null,
                Boolean.TRUE.equals(request.getServiceTermsAgreed())
                        && Boolean.TRUE.equals(request.getPrivacyPolicyAgreed())
                        && Boolean.TRUE.equals(request.getAgeOver14Agreed()),
                request.getMarketingAgreed() != null,
                !isBlank(request.getFcmToken()),
                !isBlank(request.getDeviceIdentifier())
        );

        SocialTokenInfo socialTokenInfo = verifySocialToken(request);
        log.info(
                "[TOKEN_VERIFY] success provider={} tokenVerifySuccess=true providerIdPresent={} emailPresent={}",
                request.getProvider(),
                !isBlank(socialTokenInfo.providerId()),
                !isBlank(socialTokenInfo.email())
        );

        log.info(
                "[SOCIAL_SIGNUP] social account duplicate check start provider={} providerIdPresent={}",
                request.getProvider(),
                !isBlank(socialTokenInfo.providerId())
        );
        boolean socialAccountExists = socialAccountRepository.existsByProviderAndProviderId(
                request.getProvider(),
                socialTokenInfo.providerId()
        );
        log.info(
                "[SOCIAL_SIGNUP] social account duplicate check complete provider={} providerIdPresent={} socialAccountExists={}",
                request.getProvider(),
                !isBlank(socialTokenInfo.providerId()),
                socialAccountExists
        );
        if (socialAccountExists) {
            throw new BusinessException(ErrorCode.SOCIAL_ACCOUNT_ALREADY_EXISTS);
        }

        validateSignupInput(request);

        User user = createUser(request, socialTokenInfo);
        SocialAccount socialAccount = createSocialAccount(request.getProvider(), socialTokenInfo.providerId(), user);
        log.info("[SOCIAL_SIGNUP] default setting creation start");
        try {
            inactivitySettingService.createDefaultSetting(user);
            log.info("[SOCIAL_SIGNUP] default setting creation complete success=true");
        } catch (RuntimeException e) {
            log.warn(
                    "[SOCIAL_SIGNUP] default setting creation failed success=false exceptionClass={} rootCauseClass={} constraintName={}",
                    e.getClass().getName(),
                    rootCauseClassName(e),
                    constraintName(e)
            );
            throw e;
        }

        log.info(
                "[SOCIAL_SIGNUP] fcm device registration start fcmTokenPresent={} deviceIdentifierPresent={}",
                !isBlank(request.getFcmToken()),
                !isBlank(request.getDeviceIdentifier())
        );
        try {
            registerFcmTokenIfPresent(socialAccount.getUser(), request.getFcmToken(), request.getDeviceIdentifier());
            log.info("[SOCIAL_SIGNUP] fcm device registration complete success=true");
        } catch (RuntimeException e) {
            log.warn(
                    "[SOCIAL_SIGNUP] fcm device registration failed success=false exceptionClass={} rootCauseClass={} sqlState={} constraintName={}",
                    e.getClass().getName(),
                    rootCauseClassName(e),
                    sqlState(e),
                    constraintName(e)
            );
            throw e;
        }

        log.info(
                "[JWT] refresh token creation start deviceIdentifierPresent={}",
                !isBlank(request.getDeviceIdentifier())
        );
        String refreshToken;
        try {
            refreshToken = jwtUtil.createRefreshToken(socialAccount.getUser());
            log.info("[JWT] refresh token creation complete refreshTokenCreated=true");
        } catch (RuntimeException e) {
            log.warn(
                    "[JWT] refresh token creation failed refreshTokenCreated=false exceptionClass={} rootCauseClass={} constraintName={}",
                    e.getClass().getName(),
                    rootCauseClassName(e),
                    constraintName(e)
            );
            throw e;
        }

        log.info(
                "[JWT] refresh token save start deviceIdentifierPresent={}",
                !isBlank(request.getDeviceIdentifier())
        );
        try {
            refreshTokenService.saveOrRotate(socialAccount.getUser(), request.getDeviceIdentifier(), refreshToken);
            log.info("[JWT] refresh token save complete saveOrRotateSuccess=true");
        } catch (RuntimeException e) {
            log.warn(
                    "[JWT] refresh token save failed saveOrRotateSuccess=false exceptionClass={} rootCauseClass={} sqlState={} constraintName={}",
                    e.getClass().getName(),
                    rootCauseClassName(e),
                    sqlState(e),
                    constraintName(e)
            );
            throw e;
        }

        log.info("[JWT] access token creation start");
        String accessToken;
        try {
            accessToken = jwtUtil.createAccessToken(socialAccount.getUser());
            log.info("[JWT] access token creation complete");
        } catch (RuntimeException e) {
            log.warn(
                    "[JWT] access token creation failed exceptionClass={} rootCauseClass={} constraintName={}",
                    e.getClass().getName(),
                    rootCauseClassName(e),
                    constraintName(e)
            );
            throw e;
        }

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

    private User createUser(
            SocialSignupRequest request,
            SocialTokenInfo socialTokenInfo
    ) {
        String email = blankToNull(socialTokenInfo.email());

        // 소셜 제공자에게 이메일을 받은 경우에만 중복 여부를 검사한다.
        boolean emailExists = email != null && userRepository.existsByEmail(email);
        log.info(
                "[USER_SAVE] before save emailPresent={} emailExists={} role={} birthPresent={} "
                        + "loginIdWillBeNull=true passwordWillBeNull=true",
                email != null,
                emailExists,
                request.getRole(),
                request.getBirth() != null
        );

        if (emailExists) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        try {
            User savedUser = userRepository.saveAndFlush(
                    User.builder()
                            .email(email)
                            .name(request.getName())
                            .birth(request.getBirth())
                            .role(request.getRole())
                            .status(UserStatus.ACTIVE)
                            .serviceTermsAgreed(request.getServiceTermsAgreed())
                            .privacyPolicyAgreed(request.getPrivacyPolicyAgreed())
                            .ageOver14Agreed(request.getAgeOver14Agreed())
                            .marketingAgreed(request.getMarketingAgreed())
                            .build()
            );
            log.info("[USER_SAVE] after save success=true");
            return savedUser;
        } catch (RuntimeException e) {
            log.warn(
                    "[USER_SAVE] failed success=false exceptionClass={} rootCauseClass={} sqlState={} constraintName={}",
                    e.getClass().getName(),
                    rootCauseClassName(e),
                    sqlState(e),
                    constraintName(e)
            );
            throw e;
        }
    }

    private SocialAccount createSocialAccount(LoginProvider provider, String providerId, User user) {
        log.info(
                "[SOCIAL_ACCOUNT_SAVE] before save provider={} providerIdPresent={}",
                provider,
                !isBlank(providerId)
        );
        try {
            SocialAccount savedSocialAccount = socialAccountRepository.saveAndFlush(
                    SocialAccount.builder()
                            .user(user)
                            .provider(provider)
                            .providerId(providerId)
                            .build()
            );
            log.info("[SOCIAL_ACCOUNT_SAVE] after save success=true provider={} providerIdPresent={}", provider, !isBlank(providerId));
            return savedSocialAccount;
        } catch (DataIntegrityViolationException e) {
            log.warn(
                    "[SOCIAL_ACCOUNT_SAVE] failed success=false provider={} providerIdPresent={} exceptionClass={} rootCauseClass={} constraintName={}",
                    provider,
                    !isBlank(providerId),
                    e.getClass().getName(),
                    rootCauseClassName(e),
                    constraintName(e)
            );
            throw new BusinessException(ErrorCode.SOCIAL_ACCOUNT_ALREADY_EXISTS);
        }
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void registerFcmTokenIfPresent(User user, String fcmToken, String deviceIdentifier) {
        if (!isBlank(fcmToken)) {
            deviceService.registerToken(user, fcmToken, deviceIdentifier);
        }
    }

    private String rootCauseClassName(Throwable throwable) {
        Throwable rootCause = rootCause(throwable);
        return rootCause == null ? null : rootCause.getClass().getName();
    }

    private String sqlState(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                return sqlException.getSQLState();
            }
            current = current.getCause();
        }
        return null;
    }

    private String constraintName(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof org.hibernate.exception.ConstraintViolationException constraintViolationException) {
                return constraintViolationException.getConstraintName();
            }
            current = current.getCause();
        }
        return null;
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        Throwable rootCause = throwable;
        while (current != null) {
            rootCause = current;
            current = current.getCause();
        }
        return rootCause;
    }

    private record SocialTokenInfo(
            String providerId,
            String email
    ) {
    }
}
