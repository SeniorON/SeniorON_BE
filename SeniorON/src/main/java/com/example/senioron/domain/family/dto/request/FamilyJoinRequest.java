package com.example.senioron.domain.family.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class FamilyJoinRequest {

    @NotBlank
    private String familyCode;
}