package com.example.senioron.domain.home.dto.request;

import com.example.senioron.domain.senior.entity.SeniorRelation;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record SeniorRelationUpdateRequest(

        @NotNull
        @JsonProperty("senior_id")
        Long seniorId,

        @NotNull(message = "관계를 선택해 주세요.")
        SeniorRelation relation,

        String customRelation
) {
}