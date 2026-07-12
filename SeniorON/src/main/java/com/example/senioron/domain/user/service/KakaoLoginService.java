package com.example.senioron.domain.user.service;

import com.example.senioron.domain.user.dto.kakao.response.KakaoUserInfo;
import com.example.senioron.domain.user.dto.kakao.request.KakaoLoginRequest;
import com.example.senioron.domain.user.dto.kakao.response.KakaoLoginResponse;
import com.example.senioron.domain.user.entity.LoginProvider;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.jwt.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KakaoLoginService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    public KakaoUserInfo getUserInfo(String kakaoAccessToken) {
        return RestClient.create()
                .get()
                .uri("https://kapi.kakao.com/v2/user/me")
                .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + kakaoAccessToken
                )
                .retrieve()
                .body(KakaoUserInfo.class);
    }

    @Transactional
    public KakaoLoginResponse kakaoLogin(KakaoLoginRequest request) {

        KakaoUserInfo kakaoUserInfo =
                getUserInfo(request.getKakaoAccessToken());

        validateRequiredInfo(kakaoUserInfo);

        String providerId = String.valueOf(kakaoUserInfo.getId());

        Optional<User> existingUser =
                userRepository.findByProviderAndProviderId(
                        LoginProvider.KAKAO,
                        providerId
                );

        // 기존 카카오 회원
        if (existingUser.isPresent()) {
            User user = existingUser.get();

            String accessToken = jwtUtil.createAccessToken(user);

            return KakaoLoginResponse.builder()
                    .accessToken(accessToken)
                    .usersId(user.getUsersId())
                    .name(user.getName())
                    .role(user.getRole())
                    .newUser(false)
                    .build();
        }

        // 신규 카카오 회원
        return KakaoLoginResponse.builder()
                .accessToken(null)
                .usersId(null)
                .name(kakaoUserInfo.getNickname())
                .role(null)
                .providerId(providerId)
                .newUser(true)
                .build();
    }

    private void validateRequiredInfo(KakaoUserInfo kakaoUserInfo) {

        if (kakaoUserInfo == null
                || kakaoUserInfo.getId() == null
                || kakaoUserInfo.getNickname() == null) {

            throw new IllegalArgumentException(
                    "카카오 사용자 정보를 가져오지 못했습니다."
            );
        }
    }

    private User createKakaoUser(KakaoUserInfo kakaoUserInfo) {

        LocalDate birth = convertToBirth(
                kakaoUserInfo.getBirthyear(),
                kakaoUserInfo.getBirthday()
        );

        User user = User.builder()
                .provider(LoginProvider.KAKAO)
                .providerId(String.valueOf(kakaoUserInfo.getId()))
                .email(kakaoUserInfo.getEmail())
                .name(kakaoUserInfo.getNickname())
                .birth(birth)
                .phoneNumber(normalizePhoneNumber(
                        kakaoUserInfo.getPhoneNumber()
                ))
                .managerType(ManagerType.NONE)
                .build();

        return userRepository.save(user);
    }

    private LocalDate convertToBirth(
            String birthyear,
            String birthday
    ) {
        int year = Integer.parseInt(birthyear);
        int month = Integer.parseInt(birthday.substring(0, 2));
        int day = Integer.parseInt(birthday.substring(2, 4));

        return LocalDate.of(year, month, day);
    }

    private String normalizePhoneNumber(String phoneNumber) {
        return phoneNumber
                .replace("+82 ", "0")
                .replace("-", "")
                .replace(" ", "");
    }
}