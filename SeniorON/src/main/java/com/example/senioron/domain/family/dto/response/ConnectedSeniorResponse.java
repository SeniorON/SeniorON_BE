package com.example.senioron.domain.family.dto.response;

import com.example.senioron.domain.family.entity.PhotoGroup;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.entity.SeniorRelation;
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
            Senior senior
    ) {
        return new ConnectedSeniorResponse(
                photoGroup.getId(),
                senior.getSeniorId(),
                senior.getName(),
                senior.getRelation(),
                senior.getCustomRelation(),
                photoGroup.getCreatedAt()
        );
    }
}