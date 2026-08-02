package com.example.senioron.domain.socialaccount.dto.google.response;

import com.example.senioron.domain.user.entity.Role;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GoogleLoginResponse {

    private String accessToken;
    private String refreshToken;
    private Long usersId;
    private String name;
    private Role role;
    private boolean newUser;
}
