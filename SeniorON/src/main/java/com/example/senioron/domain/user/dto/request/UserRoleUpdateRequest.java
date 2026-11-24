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

    /**
     * CHILD에서 PARENT로 변경할 때만 필수입니다.
     */
    private Boolean agreeLocationInfo;
}
