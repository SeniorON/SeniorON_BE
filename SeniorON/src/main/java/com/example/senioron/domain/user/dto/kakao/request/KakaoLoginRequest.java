package com.example.senioron.domain.user.dto.kakao.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class KakaoLoginRequest {

    @NotBlank(message = "카카오 액세스 토큰은 필수입니다.")
    private String kakaoAccessToken;
}
