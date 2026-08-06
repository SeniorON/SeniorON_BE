package com.example.senioron.domain.companion.service;

import com.example.senioron.domain.companion.config.CompanionSafetyProperties;
import com.example.senioron.domain.companion.service.model.CompanionMessageContent;
import com.example.senioron.domain.companion.service.model.SafetyDecision;
import com.example.senioron.domain.companion.service.safety.CompanionSafetyChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CompanionSafetyService {

    private final CompanionPersistenceService persistenceService;

    private final CompanionSafetyChecker safetyChecker;

    private final CompanionSafetyPushService safetyPushService;

    private final CompanionSafetyProperties properties;

    public SafetyDecision checkAndNotify(
            Long turnId,
            Long conversationId,
            Long parentUserId,
            String currentUtterance
    ) {
        List<CompanionMessageContent>
                recentMessages =
                persistenceService
                        .loadRecentMessages(
                                conversationId,
                                properties
                                        .getRecentMessageLimit()
                        );

        SafetyDecision decision =
                safetyChecker.check(
                        recentMessages,
                        currentUtterance
                );

        persistenceService.markSafetyResult(
                turnId,
                decision.type(),
                decision.ruleId()
        );

        if (decision.isEmergency()) {
            safetyPushService.sendToChildren(
                    parentUserId
            );
        }

        return decision;
    }
}