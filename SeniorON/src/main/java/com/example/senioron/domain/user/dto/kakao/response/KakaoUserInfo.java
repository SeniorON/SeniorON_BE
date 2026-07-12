package com.example.senioron.domain.user.dto.kakao.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class KakaoUserInfo {

    private Long id;

    @JsonProperty("kakao_account")
    private KakaoAccount kakaoAccount;

    @Getter
    @NoArgsConstructor
    public static class KakaoAccount {

        private String email;

        private Profile profile;

        private String birthday;

        private String birthyear;

        @JsonProperty("phone_number")
        private String phoneNumber;
    }

    @Getter
    @NoArgsConstructor
    public static class Profile {

        private String nickname;
    }

    public String getEmail() {
        return kakaoAccount != null ? kakaoAccount.getEmail() : null;
    }

    public String getNickname() {
        if (kakaoAccount == null || kakaoAccount.getProfile() == null) {
            return null;
        }

        return kakaoAccount.getProfile().getNickname();
    }

    public String getBirthday() {
        return kakaoAccount != null ? kakaoAccount.getBirthday() : null;
    }

    public String getBirthyear() {
        return kakaoAccount != null ? kakaoAccount.getBirthyear() : null;
    }

    public String getPhoneNumber() {
        return kakaoAccount != null ? kakaoAccount.getPhoneNumber() : null;
    }
}
