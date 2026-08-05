package com.example.senioron.domain.companion.service.safety;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordEmergencyRuleDetectorTest {

    private final KeywordEmergencyRuleDetector
            detector =
            new KeywordEmergencyRuleDetector();

    @Test
    void detectsBreathingEmergency() {
        Optional<String> result =
                detector.detectRuleId(
                        "지금 숨을 못 쉬겠어"
                );

        assertThat(result)
                .contains(
                        "PHYSICAL_BREATHING_001"
                );
    }

    @Test
    void detectsChestPainEmergency() {
        Optional<String> result =
                detector.detectRuleId(
                        "가슴이 너무 아파"
                );

        assertThat(result)
                .contains(
                        "PHYSICAL_CHEST_PAIN_001"
                );
    }

    @Test
    void returnsEmptyForNormalConversation() {
        Optional<String> result =
                detector.detectRuleId(
                        "오늘 점심 맛있게 먹었어"
                );

        assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyForBlankUtterance() {
        assertThat(
                detector.detectRuleId(" ")
        ).isEmpty();
    }
}