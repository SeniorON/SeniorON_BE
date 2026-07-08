package com.example.senioron.domain.user.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserLoginResponse {
    private Long usersId;
    private String name;
    private String loginId;
    private String accessToken;
}
