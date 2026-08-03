package com.example.senioron.domain.user.dto.response;

import com.example.senioron.domain.user.entity.Role;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserLoginResponse {
    private Long usersId;
    private String name;
    private String loginId;
    private Role role;
    private String accessToken;
    private String refreshToken;
}
