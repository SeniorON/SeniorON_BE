package com.example.senioron.domain.companion.dto.response;

import com.example.senioron.domain.companion.entity.FailureStage;
import com.example.senioron.domain.companion.entity.SafetyType;
import com.example.senioron.domain.companion.entity.TurnStatus;
import com.example.senioron.domain.companion.service.model.VoiceTurnOutcome;

public record CompanionVoiceTurnResponse(
        Long conversationId,
        Long turnId,
        TurnStatus turnStatus,
        VoiceTurnOutcome outcome,
        FailureStage failureStage,
        String transcript,
        String assistantText,
        SafetyType safetyType,
        String audioContentType,
        String audioFormat,
        String audioBase64
) {
}
