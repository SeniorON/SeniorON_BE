package com.example.senioron.domain.senior.dto.response;

import com.example.senioron.domain.senior.entity.Senior;

public record ParentSeniorProfileResponse(
        Long seniorId
) {

    public static ParentSeniorProfileResponse from(Senior senior) {
        return new ParentSeniorProfileResponse(
                senior.getSeniorId()
        );
    }
}
