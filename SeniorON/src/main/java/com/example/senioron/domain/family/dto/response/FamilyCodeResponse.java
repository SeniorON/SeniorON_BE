package com.example.senioron.domain.family.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FamilyCodeResponse {

    private String familyCode;
    private long familyMemberCount;

}
