package com.example.senioron.domain.senior.dto.request;

import com.example.senioron.domain.senior.entity.SeniorRelation;
import jakarta.validation.constraints.NotNull;

public record SeniorSelectionRequest(
        @NotNull(message = "시니어를 선택해 주세요.")
        Long seniorId,

        @NotNull(message = "관계를 선택해 주세요.")
        SeniorRelation relation,

        String customRelation
) {
}
