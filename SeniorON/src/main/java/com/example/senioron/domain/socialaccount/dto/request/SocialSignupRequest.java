package com.example.senioron.domain.socialaccount.dto.request;

import com.example.senioron.domain.socialaccount.entity.LoginProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SocialSignupRequest {

    @NotNull
    private LoginProvider provider;

    @NotBlank
    private String socialToken;

    @NotBlank
    private String name;

    @NotNull
    private LocalDate birth;

    @NotNull
    private Boolean serviceTermsAgreed;

    @NotNull
    private Boolean privacyPolicyAgreed;

    @NotNull
    private Boolean ageOver14Agreed;

    @NotNull
    private Boolean marketingAgreed;

    private String fcmToken;

    private String deviceIdentifier;
}
