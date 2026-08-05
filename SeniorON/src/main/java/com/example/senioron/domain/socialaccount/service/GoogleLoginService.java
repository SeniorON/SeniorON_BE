package com.example.senioron.domain.socialaccount.service;

import com.example.senioron.domain.socialaccount.dto.google.request.GoogleLoginRequest;
import com.example.senioron.domain.socialaccount.dto.google.response.GoogleLoginResponse;
import com.example.senioron.domain.socialaccount.entity.SocialAccount;
import com.example.senioron.domain.device.service.DeviceService;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.service.RefreshTokenService;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import com.example.senioron.global.jwt.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class GoogleLoginService {

    private final FirebaseIdTokenVerifier firebaseIdTokenVerifier;
    private final GoogleSocialAccountIssuer googleSocialAccountIssuer;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;
    private final DeviceService deviceService;

    public GoogleLoginResponse googleLogin(GoogleLoginRequest request) {
        VerifiedFirebaseUser firebaseUser =
                firebaseIdTokenVerifier.verify(request.getFirebaseIdToken());

        validateRequiredInfo(firebaseUser);

        GoogleSocialAccountIssuer.Result result =
                findOrCreateSocialAccount(
                        firebaseUser.uid(),
                        firebaseUser.email(),
                        firebaseUser.name()
                );

        SocialAccount socialAccount = result.socialAccount();
        User user = socialAccount.getUser();
        registerFcmTokenIfPresent(user, request.getFcmToken(), request.getDeviceIdentifier());

        String accessToken = jwtUtil.createAccessToken(user);
        String refreshToken = jwtUtil.createRefreshToken(user);
        refreshTokenService.saveOrRotate(user, request.getDeviceIdentifier(), refreshToken);

        return GoogleLoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .usersId(user.getUsersId())
                .name(user.getName())
                .role(user.getRole())
                .newUser(result.newUser())
                .build();
    }

    private GoogleSocialAccountIssuer.Result findOrCreateSocialAccount(
            String providerId,
            String email,
            String name
    ) {
        try {
            return googleSocialAccountIssuer.findOrCreate(providerId, email, name);
        } catch (DataIntegrityViolationException e) {
            return googleSocialAccountIssuer.findExisting(providerId);
        }
    }

    private void validateRequiredInfo(VerifiedFirebaseUser firebaseUser) {
        if (firebaseUser == null
                || isBlank(firebaseUser.uid())
                || isBlank(firebaseUser.email())
                || isBlank(firebaseUser.name())) {
            throw new BusinessException(ErrorCode.INVALID_FIREBASE_ID_TOKEN);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void registerFcmTokenIfPresent(User user, String fcmToken, String deviceIdentifier) {
        if (!isBlank(fcmToken)) {
            deviceService.registerToken(user, fcmToken, deviceIdentifier);
        }
    }
}
