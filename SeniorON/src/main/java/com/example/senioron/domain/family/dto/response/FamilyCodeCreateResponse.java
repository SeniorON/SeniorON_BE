package com.example.senioron.domain.family.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FamilyCodeCreateResponse {

    private Long familyId;

    private String familyCode;

}
