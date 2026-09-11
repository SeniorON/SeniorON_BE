package com.example.senioron.domain.senior.dto.response;

import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.entity.SeniorRelation;
import com.example.senioron.domain.senior.entity.UserSenior;

public record ManagedSeniorResponse(
        Long seniorId,
        String name,
        SeniorRelation relation,
        String customRelation
) {

    public static ManagedSeniorResponse from(UserSenior userSenior) {
        Senior senior = userSenior.getSenior();

        return new ManagedSeniorResponse(
                senior.getSeniorId(),
                senior.getName(),
                userSenior.getRelation(),
                userSenior.getCustomRelation()
        );
    }
}
