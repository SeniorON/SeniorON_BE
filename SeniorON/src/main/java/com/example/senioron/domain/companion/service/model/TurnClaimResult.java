package com.example.senioron.domain.companion.service.model;

import com.example.senioron.domain.companion.entity.FailureStage;
import com.example.senioron.domain.companion.entity.TurnStatus;

public record TurnClaimResult(
        TurnClaimStatus status,
        Long turnId,
        TurnStatus turnStatus,
        FailureStage retryStage
) {
}
