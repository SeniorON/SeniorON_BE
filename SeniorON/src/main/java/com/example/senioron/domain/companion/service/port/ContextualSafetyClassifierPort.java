package com.example.senioron.domain.companion.service.port;

import com.example.senioron.domain.companion.service.model.CompanionMessageContent;
import com.example.senioron.domain.companion.service.model.SafetyDecision;

import java.util.List;

public interface ContextualSafetyClassifierPort {

    SafetyDecision classify(
            List<CompanionMessageContent> recentMessages,
            String currentUtterance
    );
}