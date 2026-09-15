package com.example.senioron.domain.senior.dto.response;

import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.entity.SeniorRelation;

public record ManagedSeniorResponse(
        Long familyId,
        Long seniorId,
        Long parentUserId,
        String name,
        SeniorRelation relation,
        String customRelation
) {

    public static ManagedSeniorResponse from(Senior senior) {
        return new ManagedSeniorResponse(
                senior.getFamily().getFamilyId(),
                senior.getSeniorId(),
                senior.getParentUser() == null
                        ? null
                        : senior.getParentUser().getUsersId(),
                senior.getName(),
                senior.getRelation(),
                senior.getCustomRelation()
        );
    }
}
