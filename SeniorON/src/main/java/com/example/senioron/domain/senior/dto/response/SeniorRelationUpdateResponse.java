package com.example.senioron.domain.senior.dto.response;

import com.example.senioron.domain.senior.entity.SeniorRelation;

public record SeniorRelationUpdateResponse(
        Long seniorId,
        SeniorRelation relation,
        String customRelation
) {

    public static SeniorRelationUpdateResponse from(
            Long seniorId,
            SeniorRelation relation,
            String customRelation
    ) {
        return new SeniorRelationUpdateResponse(
                seniorId,
                relation,
                customRelation
        );
    }
}
