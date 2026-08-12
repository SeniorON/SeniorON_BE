package com.example.senioron.domain.user.dto.response;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginIdFindResponse {

    private String loginId;

    @NotNull
    private LocalDateTime createdAt;
}
