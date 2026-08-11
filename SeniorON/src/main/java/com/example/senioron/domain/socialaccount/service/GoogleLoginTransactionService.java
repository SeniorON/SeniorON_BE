package com.example.senioron.domain.socialaccount.service;

import com.example.senioron.domain.device.service.DeviceService;
import com.example.senioron.domain.socialaccount.dto.google.request.GoogleLoginRequest;
import com.example.senioron.domain.socialaccount.dto.google.response.GoogleLoginResponse;
import com.example.senioron.domain.socialaccount.entity.LoginProvider;
import com.example.senioron.domain.socialaccount.entity.SocialAccount;
import com.example.senioron.domain.socialaccount.repository.SocialAccountRepository;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.entity.UserStatus;
import com.example.senioron.domain.user.service.RefreshTokenService;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import com.example.senioron.global.jwt.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GoogleLoginTransactionService {

    private final SocialAccountRepository socialAccountRepository;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;
    private final DeviceService deviceService;

    @Transactional
    public GoogleLoginResponse login(GoogleLoginRequest request, VerifiedFirebaseUser firebaseUser) {
        return socialAccountRepository.findByProviderAndProviderId(
                        LoginProvider.GOOGLE,
                        firebaseUser.uid()
                )
                .map(socialAccount -> loginExistingUser(socialAccount, request))
                .orElseGet(() -> createNewUserResponse(firebaseUser));
    }

    private GoogleLoginResponse loginExistingUser(
            SocialAccount socialAccount,
            GoogleLoginRequest request
    ) {
        User user = socialAccount.getUser();
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.WITHDRAWN_USER);
        }

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
                .newUser(false)
                .build();
    }

    private GoogleLoginResponse createNewUserResponse(VerifiedFirebaseUser firebaseUser) {
        return GoogleLoginResponse.builder()
                .accessToken(null)
                .refreshToken(null)
                .usersId(null)
                .name(firebaseUser.name())
                .role(null)
                .newUser(true)
                .build();
    }

    private void registerFcmTokenIfPresent(User user, String fcmToken, String deviceIdentifier) {
        if (fcmToken != null && !fcmToken.isBlank()) {
            deviceService.registerToken(user, fcmToken, deviceIdentifier);
        }
    }
}
