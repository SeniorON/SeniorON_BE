package com.example.senioron.domain.family.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SeniorCodeResponse {

    private String seniorCode;
    private long familyMemberCount;

}
