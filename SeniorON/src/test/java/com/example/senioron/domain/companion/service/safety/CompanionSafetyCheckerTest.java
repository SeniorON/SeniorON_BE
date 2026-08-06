package com.example.senioron.domain.companion.service.safety;

import com.example.senioron.domain.companion.entity.MessageRole;
import com.example.senioron.domain.companion.entity.SafetyType;
import com.example.senioron.domain.companion.service.model.CompanionMessageContent;
import com.example.senioron.domain.companion.service.model.SafetyDecision;
import com.example.senioron.domain.companion.service.port.ContextualSafetyClassifierPort;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CompanionSafetyCheckerTest {

    private final EmergencyRuleDetector
            emergencyRuleDetector =
            mock(EmergencyRuleDetector.class);

    private final ContextualSafetyClassifierPort
            contextualSafetyClassifier =
            mock(
                    ContextualSafetyClassifierPort.class
            );

    private CompanionSafetyChecker checker;

    @BeforeEach
    void setUp() {
        checker =
                new CompanionSafetyChecker(
                        emergencyRuleDetector,
                        contextualSafetyClassifier
                );
    }

    @Test
    void confirmsMatchedEmergencyRuleWithContextClassifier() {
        String utterance =
                "숨을 못 쉬겠어";

        given(
                emergencyRuleDetector
                        .detectRuleId(utterance)
        ).willReturn(
                Optional.of(
                        "PHYSICAL_BREATHING_001"
                )
        );

        given(
                contextualSafetyClassifier
                        .classify(
                                List.of(),
                                utterance
                        )
        ).willReturn(
                SafetyDecision.emergency(
                        "CONTEXTUAL_CLASSIFIER_V1"
                )
        );

        SafetyDecision result =
                checker.check(
                        List.of(),
                        utterance
                );

        assertThat(result.type())
                .isEqualTo(
                        SafetyType.EMERGENCY
                );

        assertThat(result.ruleId())
                .isEqualTo(
                        "PHYSICAL_BREATHING_001"
                );

        verify(
                contextualSafetyClassifier
        ).classify(
                List.of(),
                utterance
        );
    }
    @Test
    void returnsNormalWhenMatchedRuleIsNegatedByContext() {
        String utterance =
                "아까는 숨을 못 쉬겠어 했는데 지금은 괜찮아";

        given(
                emergencyRuleDetector
                        .detectRuleId(utterance)
        ).willReturn(
                Optional.of(
                        "PHYSICAL_BREATHING_001"
                )
        );

        given(
                contextualSafetyClassifier
                        .classify(
                                List.of(),
                                utterance
                        )
        ).willReturn(
                SafetyDecision.normal()
        );

        SafetyDecision result =
                checker.check(
                        List.of(),
                        utterance
                );

        assertThat(result.isEmergency())
                .isFalse();
    }


    @Test
    void usesContextClassifierWhenRuleDoesNotMatch() {
        String utterance =
                "그냥 다 포기하고 싶어";

        List<CompanionMessageContent>
                recentMessages =
                List.of(
                        new CompanionMessageContent(
                                MessageRole.USER,
                                "요즘 계속 힘들어"
                        )
                );

        given(
                emergencyRuleDetector
                        .detectRuleId(utterance)
        ).willReturn(Optional.empty());

        given(
                contextualSafetyClassifier
                        .classify(
                                recentMessages,
                                utterance
                        )
        ).willReturn(
                SafetyDecision.emergency(
                        "CONTEXTUAL_CLASSIFIER_V1"
                )
        );

        SafetyDecision result =
                checker.check(
                        recentMessages,
                        utterance
                );

        assertThat(result.isEmergency())
                .isTrue();

        verify(
                contextualSafetyClassifier
        ).classify(
                recentMessages,
                utterance
        );
    }
    @Test
    void returnsNormalWhenMatchedRuleIsQuotedByAnotherPerson() {
        String utterance =
                "친구가 숨을 못 쉬겠어라고 말했어";

        given(
                emergencyRuleDetector
                        .detectRuleId(utterance)
        ).willReturn(
                Optional.of(
                        "PHYSICAL_BREATHING_001"
                )
        );

        given(
                contextualSafetyClassifier
                        .classify(
                                List.of(),
                                utterance
                        )
        ).willReturn(
                SafetyDecision.normal()
        );

        SafetyDecision result =
                checker.check(
                        List.of(),
                        utterance
                );

        assertThat(result.isEmergency())
                .isFalse();

        verify(
                contextualSafetyClassifier
        ).classify(
                List.of(),
                utterance
        );
    }

    @Test
    void fallsBackToMatchedRuleWhenClassifierFails() {
        String utterance =
                "숨을 못 쉬겠어";

        given(
                emergencyRuleDetector
                        .detectRuleId(utterance)
        ).willReturn(
                Optional.of(
                        "PHYSICAL_BREATHING_001"
                )
        );

        given(
                contextualSafetyClassifier
                        .classify(
                                List.of(),
                                utterance
                        )
        ).willThrow(
                new BusinessException(
                        ErrorCode
                                .COMPANION_SAFETY_CLASSIFIER_UNAVAILABLE
                )
        );

        SafetyDecision result =
                checker.check(
                        List.of(),
                        utterance
                );

        assertThat(result.isEmergency())
                .isTrue();

        assertThat(result.ruleId())
                .isEqualTo(
                        "PHYSICAL_BREATHING_001"
                );
    }
}