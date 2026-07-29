package com.example.senioron.domain.companion.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.entity.UserStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CompanionTurnTest {

    @Test
    void processesNormalTurnAndRestoresLastSuccessfulStatus() {
        CompanionTurn turn =
                createReceivedTurn();

        turn.markTranscribed(
                "OPENAI",
                "gpt-4o-mini-transcribe"
        );

        turn.markSafetyResult(
                SafetyType.NORMAL,
                null
        );

        turn.markResponseGenerated(
                "ANTHROPIC",
                "claude-haiku",
                "companion-v1",
                100,
                30
        );

        assertThat(turn.getStatus())
                .isEqualTo(
                        TurnStatus.RESPONSE_GENERATED
                );

        turn.markFailed(FailureStage.TTS);

        assertThat(turn.getStatus())
                .isEqualTo(TurnStatus.FAILED);

        assertThat(turn.getFailureStage())
                .isEqualTo(FailureStage.TTS);

        turn.prepareRetry();

        assertThat(turn.getStatus())
                .isEqualTo(
                        TurnStatus.RESPONSE_GENERATED
                );

        assertThat(turn.getFailureStage())
                .isNull();

        turn.markCompleted(
                "NAVER_CLOVA",
                "nara"
        );

        assertThat(turn.getStatus())
                .isEqualTo(TurnStatus.COMPLETED);
    }

    @Test
    void responseCannotBeGeneratedBeforeSafetyCheck() {
        CompanionTurn turn =
                createReceivedTurn();

        turn.markTranscribed(
                "OPENAI",
                "gpt-4o-mini-transcribe"
        );

        assertThatThrownBy(() ->
                turn.markResponseGenerated(
                        "ANTHROPIC",
                        "claude-haiku",
                        "companion-v1",
                        100,
                        30
                )
        ).isInstanceOf(
                IllegalStateException.class
        );
    }

    @Test
    void completedTurnCannotBeMarkedFailed() {
        CompanionTurn turn =
                createCompletedTurn();

        assertThatThrownBy(() ->
                turn.markFailed(FailureStage.TTS)
        ).isInstanceOf(
                IllegalStateException.class
        );
    }

    private CompanionTurn createReceivedTurn() {
        return CompanionTurn.receive(
                createActiveConversation(),
                UUID.randomUUID().toString()
        );
    }

    private CompanionTurn createCompletedTurn() {
        CompanionTurn turn =
                createReceivedTurn();

        turn.markTranscribed(
                "OPENAI",
                "gpt-4o-mini-transcribe"
        );

        turn.markSafetyResult(
                SafetyType.NORMAL,
                null
        );

        turn.markResponseGenerated(
                "ANTHROPIC",
                "claude-haiku",
                "companion-v1",
                100,
                30
        );

        turn.markCompleted(
                "NAVER_CLOVA",
                "nara"
        );

        return turn;
    }

    private CompanionConversation
    createActiveConversation() {
        User parent =
                User.builder()
                        .usersId(1L)
                        .name("테스트 부모")
                        .role(Role.PARENT)
                        .status(UserStatus.ACTIVE)
                        .build();

        return CompanionConversation.builder()
                .conversationId(1L)
                .user(parent)
                .status(ConversationStatus.ACTIVE)
                .build();
    }
}