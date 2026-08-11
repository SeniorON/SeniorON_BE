package com.example.senioron.domain.companion.service.model;

import com.example.senioron.domain.companion.entity.FailureStage;
import com.example.senioron.domain.companion.entity.SafetyType;
import com.example.senioron.domain.companion.entity.TurnStatus;

public record CompanionTurnSnapshot(
        Long conversationId,
        Long turnId,
        TurnStatus turnStatus,
        FailureStage failureStage,
        SafetyType safetyType,
        String transcript,
        String assistantText
) {
}