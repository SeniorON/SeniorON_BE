package com.example.senioron.domain.senior.dto.request;

import jakarta.validation.constraints.NotNull;

public record SeniorParentLinkRequest(
        @NotNull
        Long seniorId
) {
}
