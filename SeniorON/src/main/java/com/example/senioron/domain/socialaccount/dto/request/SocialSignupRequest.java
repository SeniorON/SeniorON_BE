package com.example.senioron.domain.socialaccount.dto.request;

import com.example.senioron.domain.socialaccount.entity.LoginProvider;
import com.example.senioron.domain.user.entity.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SocialSignupRequest {

    @NotNull(message = "로그인 제공자를 선택해 주세요.")
    private LoginProvider provider;

    private String socialToken;

    @NotBlank(message = "이름을 입력해 주세요.")
    private String name;

    @NotNull(message = "생년월일을 입력해 주세요.")
    private LocalDate birth;

    @NotNull(message = "회원 역할을 선택해 주세요.")
    private Role role;

    @NotNull(message = "서비스 이용약관 동의 여부를 입력해 주세요.")
    private Boolean serviceTermsAgreed;

    @NotNull(message = "개인정보 처리방침 동의 여부를 입력해 주세요.")
    private Boolean privacyPolicyAgreed;

    @NotNull(message = "만 14세 이상 동의 여부를 입력해 주세요.")
    private Boolean ageOver14Agreed;

    @NotNull(message = "마케팅 정보 수신 동의 여부를 입력해 주세요.")
    private Boolean marketingAgreed;

    private String fcmToken;

    private String deviceIdentifier;
}
