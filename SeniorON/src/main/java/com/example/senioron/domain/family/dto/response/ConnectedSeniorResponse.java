package com.example.senioron.domain.family.dto.response;

import com.example.senioron.domain.family.entity.PhotoGroup;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.entity.SeniorRelation;
import com.example.senioron.domain.family.entity.FamilyMember;
import java.time.LocalDateTime;

public record ConnectedSeniorResponse(
        Long photoGroupId,
        Long seniorId,
        String name,
        SeniorRelation relation,
        String customRelation,
        LocalDateTime connectedAt
) {

    public static ConnectedSeniorResponse from(
            PhotoGroup photoGroup,
            Senior senior,
            FamilyMember familyMember
    ) {
        return new ConnectedSeniorResponse(
                photoGroup.getId(),
                senior.getSeniorId(),
                senior.getName(),
                familyMember != null ? familyMember.getRelation() : null,
                familyMember != null ? familyMember.getCustomRelation() : null,
                photoGroup.getCreatedAt()
        );
    }
}