package com.example.senioron.domain.family.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class FamilyPrimaryManagerUpdateRequest {

    @NotNull
    private Long targetUserId;
}
