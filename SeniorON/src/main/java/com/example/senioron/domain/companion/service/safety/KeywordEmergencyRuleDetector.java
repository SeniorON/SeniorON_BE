package com.example.senioron.domain.companion.service.safety;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class KeywordEmergencyRuleDetector
        implements EmergencyRuleDetector {

    private static final List<EmergencyRule>
            RULES = List.of(
            new EmergencyRule(
                    "PHYSICAL_BREATHING_001",
                    List.of(
                            "숨을 못 쉬겠어",
                            "숨이 안 쉬어져",
                            "숨을 쉴 수 없어"
                    )
            ),
            new EmergencyRule(
                    "PHYSICAL_CHEST_PAIN_001",
                    List.of(
                            "가슴이 너무 아파",
                            "가슴 통증이 너무 심해",
                            "가슴이 심하게 조여"
                    )
            ),
            new EmergencyRule(
                    "PHYSICAL_COLLAPSE_001",
                    List.of(
                            "지금 쓰러졌어",
                            "곧 쓰러질 것 같아",
                            "의식을 잃을 것 같아"
                    )
            )
    );

    @Override
    public Optional<String> detectRuleId(
            String currentUtterance
    ) {
        if (currentUtterance == null
                || currentUtterance.isBlank()) {

            return Optional.empty();
        }

        String normalized =
                normalize(currentUtterance);

        return RULES.stream()
                .filter(rule ->
                        rule.matches(normalized)
                )
                .map(EmergencyRule::ruleId)
                .findFirst();
    }

    private String normalize(
            String utterance
    ) {
        return utterance
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "");
    }

    private record EmergencyRule(
            String ruleId,
            List<String> phrases
    ) {
        private boolean matches(
                String utterance
        ) {
            return phrases.stream()
                    .map(phrase ->
                            phrase.replaceAll(
                                    "\\s+",
                                    ""
                            )
                    )
                    .anyMatch(
                            utterance::contains
                    );
        }
    }
}