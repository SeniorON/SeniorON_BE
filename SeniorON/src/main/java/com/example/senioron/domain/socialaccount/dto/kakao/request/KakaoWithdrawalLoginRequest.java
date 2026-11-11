package com.example.senioron.domain.socialaccount.dto.kakao.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class KakaoWithdrawalLoginRequest {

    @NotBlank(message = "카카오 인가 코드는 필수입니다.")
    private String code;
}
