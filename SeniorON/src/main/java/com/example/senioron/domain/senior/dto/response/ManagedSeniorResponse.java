package com.example.senioron.domain.senior.dto.response;

import com.example.senioron.domain.senior.entity.SeniorRelation;

public record ManagedSeniorResponse(
        Long familyId,
        Long seniorId,
        String seniorName,
        SeniorRelation relation,
        String customRelation,
        boolean seniorProfileCompleted
) {
}
