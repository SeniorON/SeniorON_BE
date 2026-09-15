package com.example.senioron.domain.senior.dto.response;

import com.example.senioron.domain.senior.entity.SeniorRelation;
import com.example.senioron.domain.senior.entity.Senior;

public record SeniorRelationUpdateResponse(
        Long seniorId,
        SeniorRelation relation,
        String customRelation
) {

    public static SeniorRelationUpdateResponse from(
            Senior senior
    ) {
        return new SeniorRelationUpdateResponse(
                senior.getSeniorId(),
                senior.getRelation(),
                senior.getCustomRelation()
        );
    }
}
