package com.example.senioron.domain.senior.dto.response;

import com.example.senioron.domain.senior.entity.SeniorRelation;
import com.example.senioron.domain.senior.entity.UserSenior;

public record SeniorRelationUpdateResponse(
        Long seniorId,
        SeniorRelation relation,
        String customRelation
) {

    public static SeniorRelationUpdateResponse from(UserSenior userSenior) {
        return new SeniorRelationUpdateResponse(
                userSenior.getSenior().getSeniorId(),
                userSenior.getRelation(),
                userSenior.getCustomRelation()
        );
    }
}
