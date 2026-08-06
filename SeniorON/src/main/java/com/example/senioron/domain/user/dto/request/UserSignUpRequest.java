package com.example.senioron.domain.user.dto.request;


import com.example.senioron.domain.user.entity.Role;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class UserSignUpRequest {

    @NotBlank(message = "아이디를 입력해 주세요.")
    private String loginId;

    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @NotBlank(message = "이메일을 입력해 주세요.")
    private String email;

    @NotBlank(message = "비밀번호를 입력해 주세요.")
    private String password;

    @NotBlank(message = "비밀번호 확인을 입력해 주세요.")
    private String passwordCheck;

    @NotBlank(message = "이름을 입력해 주세요.")
    private String name;

    @NotNull(message = "생년월일을 입력해 주세요.")
    private LocalDate birth;

    @NotNull(message = "회원 역할을 선택해 주세요.")
    private Role role;

    @AssertTrue(message = "서비스 이용약관에 동의해 주세요.")
    private Boolean agreeServiceTerms;

    @AssertTrue(message = "개인정보 처리방침에 동의해 주세요.")
    private Boolean agreePrivacyPolicy;

    @AssertTrue(message = "만 14세 이상 동의가 필요합니다.")
    private Boolean agreeAgeOver14;

    private Boolean agreeMarketing;
}
