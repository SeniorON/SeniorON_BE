package com.example.senioron.domain.socialaccount.dto.kakao.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class KakaoAccessTokenInfo {

    private Long id;

    @JsonProperty("expires_in")
    private Long expiresIn;

    @JsonProperty("app_id")
    private Long appId;
}
