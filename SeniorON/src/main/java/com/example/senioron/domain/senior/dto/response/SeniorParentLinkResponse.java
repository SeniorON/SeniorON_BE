package com.example.senioron.domain.senior.dto.response;

import com.example.senioron.domain.senior.entity.Senior;

public record SeniorParentLinkResponse(
        Long seniorId,
        String name,
        Long parentUserId
) {

    public static SeniorParentLinkResponse from(Senior senior) {
        return new SeniorParentLinkResponse(
                senior.getSeniorId(),
                senior.getName(),
                senior.getParentUser().getUsersId()
        );
    }
}
