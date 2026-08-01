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

    @NotBlank
    private String loginId;

    @Email
    @NotBlank
    private String email;

    @NotBlank
    private String password;

    @NotBlank
    private String passwordCheck;

    @NotBlank
    private String name;

    @NotNull
    private LocalDate birth;

    @NotNull
    private Role role;

    @AssertTrue
    private Boolean agreeServiceTerms;

    @AssertTrue
    private Boolean agreePrivacyPolicy;

    @AssertTrue
    private Boolean agreeAgeOver14;

    private Boolean agreeMarketing;
}
