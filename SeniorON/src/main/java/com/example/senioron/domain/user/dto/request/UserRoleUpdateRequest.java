package com.example.senioron.domain.user.dto.request;

import com.example.senioron.domain.user.entity.Role;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserRoleUpdateRequest {

    @NotNull
    private Role role;
}