package com.example.senioron.domain.companion.service.safety;

import com.example.senioron.domain.companion.service.model.CompanionMessageContent;
import com.example.senioron.domain.companion.service.model.SafetyDecision;
import com.example.senioron.domain.companion.service.port.ContextualSafetyClassifierPort;
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
        Optional<String> matchedRuleId =
                emergencyRuleDetector.detectRuleId(
                        currentUtterance
                );

        if (matchedRuleId.isPresent()) {
            return SafetyDecision.emergency(
                    matchedRuleId.get()
            );
        }

        return contextualSafetyClassifier.classify(
                recentMessages,
                currentUtterance
        );
    }
}