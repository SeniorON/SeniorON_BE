package com.example.senioron.domain.socialaccount.service;

import com.example.senioron.domain.socialaccount.client.KakaoUserClient;
import com.example.senioron.domain.socialaccount.dto.kakao.response.KakaoUserInfo;
import com.example.senioron.domain.socialaccount.dto.request.SocialSignupRequest;
import com.example.senioron.domain.socialaccount.dto.response.SocialSignupResponse;
import com.example.senioron.domain.socialaccount.entity.LoginProvider;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Service
@RequiredArgsConstructor
public class SocialSignupService {

    private final KakaoUserClient kakaoUserClient;
    private final FirebaseIdTokenVerifier firebaseIdTokenVerifier;
    private final SocialSignupTransactionService socialSignupTransactionService;

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

        return socialSignupTransactionService.signup(
                request,
                socialTokenInfo.providerId(),
                socialTokenInfo.email()
        );
    }

    private SocialTokenInfo verifySocialToken(SocialSignupRequest request) {
        if (isBlank(request.getSocialToken())) {
            throw new BusinessException(ErrorCode.INVALID_SOCIAL_TOKEN);
        }

        if (request.getProvider() == LoginProvider.KAKAO) {
            KakaoUserInfo kakaoUserInfo;
            try {
                kakaoUserInfo = kakaoUserClient.getUserInfo(request.getSocialToken());
            } catch (RestClientResponseException e) {
                log.warn(
                        "[TOKEN_VERIFY] failed provider={} tokenVerifySuccess=false exceptionClass={} rootCauseClass={} httpStatus={}",
                        request.getProvider(),
                        e.getClass().getName(),
                        rootCauseClassName(e),
                        e.getStatusCode()
                );
                if (isAuthenticationRejected(e)) {
                    throw new BusinessException(ErrorCode.INVALID_SOCIAL_TOKEN);
                }
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            } catch (RestClientException e) {
                log.warn(
                        "[TOKEN_VERIFY] failed provider={} tokenVerifySuccess=false exceptionClass={} rootCauseClass={}",
                        request.getProvider(),
                        e.getClass().getName(),
                        rootCauseClassName(e)
                );
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            }

            if (kakaoUserInfo == null || kakaoUserInfo.getId() == null) {
                throw new BusinessException(ErrorCode.INVALID_SOCIAL_TOKEN);
            }

            return new SocialTokenInfo(String.valueOf(kakaoUserInfo.getId()), kakaoUserInfo.getEmail());
        }

        if (request.getProvider() == LoginProvider.GOOGLE) {
            VerifiedFirebaseUser firebaseUser;
            try {
                firebaseUser = firebaseIdTokenVerifier.verify(request.getSocialToken());
            } catch (BusinessException e) {
                if (e.getCode() == ErrorCode.INVALID_FIREBASE_ID_TOKEN) {
                    throw new BusinessException(ErrorCode.INVALID_SOCIAL_TOKEN);
                }
                throw e;
            }

            if (firebaseUser == null || isBlank(firebaseUser.uid())) {
                throw new BusinessException(ErrorCode.INVALID_SOCIAL_TOKEN);
            }

            return new SocialTokenInfo(firebaseUser.uid(), firebaseUser.email());
        }

        throw new BusinessException(ErrorCode.BAD_REQUEST);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isAuthenticationRejected(RestClientResponseException exception) {
        return exception.getStatusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED)
                || exception.getStatusCode().isSameCodeAs(HttpStatus.FORBIDDEN);
    }

    private String rootCauseClassName(Throwable throwable) {
        Throwable rootCause = rootCause(throwable);
        return rootCause == null ? null : rootCause.getClass().getName();
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
