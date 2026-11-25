package com.example.senioron.domain.user.dto.response;

import com.example.senioron.domain.user.entity.Role;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserSignUpResponse {

    private Long usersId;
    private String name;
    private String loginId;
    private Role role;
}
