package com.example.senioron.domain.companion.service.safety;

import com.example.senioron.domain.companion.service.model.CompanionMessageContent;
import com.example.senioron.domain.companion.service.model.SafetyDecision;
import com.example.senioron.domain.companion.service.port.ContextualSafetyClassifierPort;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CompanionSafetyChecker {

    private final EmergencyRuleDetector emergencyRuleDetector;

    private final ContextualSafetyClassifierPort contextualSafetyClassifier;

    public SafetyDecision check(
            List<CompanionMessageContent> recentMessages,
            String currentUtterance
    ) {
        Optional<String> candidateRuleId =
                emergencyRuleDetector.detectRuleId(
                        currentUtterance
                );

        try {
            SafetyDecision contextualDecision =
                    contextualSafetyClassifier.classify(
                            recentMessages,
                            currentUtterance
                    );

            if (candidateRuleId.isPresent()
                    && contextualDecision.isEmergency()) {

                return SafetyDecision.emergency(
                        candidateRuleId.get()
                );
            }

            return contextualDecision;

        } catch (BusinessException exception) {
            if (candidateRuleId.isPresent()) {
                return SafetyDecision.emergency(
                        candidateRuleId.get()
                );
            }

            throw exception;
        }
    }
}