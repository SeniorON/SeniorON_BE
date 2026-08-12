package com.example.senioron.domain.socialaccount.service;

import com.example.senioron.domain.device.service.DeviceService;
import com.example.senioron.domain.socialaccount.dto.kakao.response.KakaoLoginResponse;
import com.example.senioron.domain.socialaccount.dto.kakao.response.KakaoUserInfo;
import com.example.senioron.domain.socialaccount.dto.kakao.request.KakaoLoginRequest;
import com.example.senioron.domain.socialaccount.entity.LoginProvider;
import com.example.senioron.domain.socialaccount.entity.SocialAccount;
import com.example.senioron.domain.socialaccount.repository.SocialAccountRepository;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.entity.UserStatus;
import com.example.senioron.domain.user.service.RefreshTokenService;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import com.example.senioron.global.jwt.JwtUtil;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KakaoLoginTransactionService {

    private final SocialAccountRepository socialAccountRepository;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;
    private final DeviceService deviceService;

    @Transactional
    public KakaoLoginResponse login(KakaoLoginRequest request, KakaoUserInfo kakaoUserInfo) {
        String providerId = String.valueOf(kakaoUserInfo.getId());

        Optional<SocialAccount> existingSocialAccount =
                socialAccountRepository.findByProviderAndProviderId(
                        LoginProvider.KAKAO,
                        providerId
                );

        // 기존 카카오 회원인 경우 JWT 발급 후 로그인
        if (existingSocialAccount.isPresent()) {
            User user = existingSocialAccount.get().getUser();
            if (user.getStatus() == UserStatus.WITHDRAWN) {
                throw new BusinessException(ErrorCode.WITHDRAWN_USER);
            }

            String accessToken = jwtUtil.createAccessToken(user);
            String refreshToken = jwtUtil.createRefreshToken(user);
            registerFcmTokenIfPresent(user, request.getFcmToken(), request.getDeviceIdentifier());
            refreshTokenService.saveOrRotate(user, request.getDeviceIdentifier(), refreshToken);

            return KakaoLoginResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .usersId(user.getUsersId())
                    .name(user.getName())
                    .role(user.getRole())
                    .newUser(false)
                    .build();
        }

        // 신규 카카오 회원인 경우 추가 회원가입 진행
        return KakaoLoginResponse.builder()
                .accessToken(null)
                .usersId(null)
                .name(kakaoUserInfo.getNickname())
                .role(null)
                .providerId(providerId)
                .newUser(true)
                .build();
    }

    private void registerFcmTokenIfPresent(User user, String fcmToken, String deviceIdentifier) {
        if (fcmToken != null && !fcmToken.isBlank()) {
            deviceService.registerToken(user, fcmToken, deviceIdentifier);
        }
    }
}
