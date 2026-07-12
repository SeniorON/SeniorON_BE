package com.example.senioron.domain.user.dto.kakao.response;

import com.example.senioron.domain.user.entity.Role;
import lombok.Builder;
import lombok.Getter;


@Getter
@Builder
public class KakaoLoginResponse {

    private String accessToken;
    private Long usersId;
    private String name;
    private Role role;

    private String providerId;
    private boolean newUser;
}
