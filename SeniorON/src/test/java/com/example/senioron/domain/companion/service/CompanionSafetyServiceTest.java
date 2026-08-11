package com.example.senioron.domain.companion.service;

import com.example.senioron.domain.companion.config.CompanionSafetyProperties;
import com.example.senioron.domain.companion.entity.MessageRole;
import com.example.senioron.domain.companion.entity.SafetyType;
import com.example.senioron.domain.companion.service.model.CompanionMessageContent;
import com.example.senioron.domain.companion.service.model.SafetyDecision;
import com.example.senioron.domain.companion.service.safety.CompanionSafetyChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CompanionSafetyServiceTest {

    private static final Long TURN_ID = 10L;
    private static final Long CONVERSATION_ID = 20L;
    private static final Long PARENT_USER_ID = 30L;

    private final CompanionPersistenceService
            persistenceService =
            mock(
                    CompanionPersistenceService.class
            );

    private final CompanionSafetyChecker
            safetyChecker =
            mock(
                    CompanionSafetyChecker.class
            );

    private final CompanionSafetyPushService
            safetyPushService =
            mock(
                    CompanionSafetyPushService.class
            );

    private CompanionSafetyService service;

    @BeforeEach
    void setUp() {
        CompanionSafetyProperties properties =
                new CompanionSafetyProperties();

        properties.setRecentMessageLimit(6);

        service =
                new CompanionSafetyService(
                        persistenceService,
                        safetyChecker,
                        safetyPushService,
                        properties
                );
    }

    @Test
    void storesNormalResultWithoutPushNotification() {
        String utterance =
                "오늘 날씨가 좋네";

        List<CompanionMessageContent>
                recentMessages =
                List.of(
                        new CompanionMessageContent(
                                MessageRole.ASSISTANT,
                                "오늘 기분은 어떠세요?"
                        )
                );

        given(
                persistenceService
                        .loadRecentMessagesExcludingTurn(
                                CONVERSATION_ID,
                                TURN_ID,
                                6
                        )
        ).willReturn(recentMessages);

        given(
                safetyChecker.check(
                        recentMessages,
                        utterance
                )
        ).willReturn(
                SafetyDecision.normal()
        );

        SafetyDecision result =
                service.checkAndNotify(
                        TURN_ID,
                        CONVERSATION_ID,
                        PARENT_USER_ID,
                        utterance
                );

        assertThat(result.type())
                .isEqualTo(
                        SafetyType.NORMAL
                );

        verify(
                persistenceService
        ).markSafetyResult(
                TURN_ID,
                SafetyType.NORMAL,
                null
        );

        verify(
                safetyPushService,
                never()
        ).sendToChildren(
                PARENT_USER_ID
        );
    }

    @Test
    void sendsPushNotificationForEmergencyResult() {
        String utterance =
                "숨을 못 쉬겠어";

        List<CompanionMessageContent>
                recentMessages =
                List.of();

        SafetyDecision emergency =
                SafetyDecision.emergency(
                        "PHYSICAL_BREATHING_001"
                );

        given(
                persistenceService
                        .loadRecentMessagesExcludingTurn(
                                CONVERSATION_ID,
                                TURN_ID,
                                6
                        )
        ).willReturn(recentMessages);

        given(
                safetyChecker.check(
                        recentMessages,
                        utterance
                )
        ).willReturn(emergency);

        SafetyDecision result =
                service.checkAndNotify(
                        TURN_ID,
                        CONVERSATION_ID,
                        PARENT_USER_ID,
                        utterance
                );

        assertThat(result.isEmergency())
                .isTrue();

        verify(
                persistenceService
        ).markSafetyResult(
                TURN_ID,
                SafetyType.EMERGENCY,
                "PHYSICAL_BREATHING_001"
        );

        verify(
                safetyPushService
        ).sendToChildren(
                PARENT_USER_ID
        );
    }
}