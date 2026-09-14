package com.example.senioron.domain.senior.dto.response;

import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.entity.SeniorRelation;

public record ManagedSeniorResponse(
        Long seniorId,
        String name,
        SeniorRelation relation,
        String customRelation
) {

    public static ManagedSeniorResponse from(Senior senior) {
        return new ManagedSeniorResponse(
                senior.getSeniorId(),
                senior.getName(),
                null,
                null
        );
    }
}
