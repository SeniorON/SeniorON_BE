package com.example.senioron.domain.companion.entity;

import com.example.senioron.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(
        name = "companion_turns",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_companion_turn_conversation_request",
                        columnNames = {
                                "conversation_id",
                                "request_id"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_companion_turn_conversation_created",
                        columnList = "conversation_id,created_at"
                ),
                @Index(
                        name = "idx_companion_turn_conversation_status",
                        columnList = "conversation_id,status"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CompanionTurn extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "turn_id")
    private Long turnId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private CompanionConversation conversation;

    @Column(name = "request_id", nullable = false, length = 36)
    private String requestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TurnStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "safety_type")
    private SafetyType safetyType;

    @Column(name = "safety_rule_id")
    private String safetyRuleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_stage")
    private FailureStage failureStage;

    @Column(name = "stt_provider")
    private String sttProvider;

    @Column(name = "stt_model")
    private String sttModel;

    @Column(name = "llm_provider")
    private String llmProvider;

    @Column(name = "llm_model")
    private String llmModel;

    @Column(name = "tts_provider")
    private String ttsProvider;

    @Column(name = "tts_voice")
    private String ttsVoice;

    @Column(name = "prompt_version")
    private String promptVersion;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    public static CompanionTurn receive(
            CompanionConversation conversation,
            String requestId
    ) {
        if (conversation == null) {
            throw new IllegalArgumentException("대화는 null일 수 없습니다.");
        }

        if (!conversation.isActive()) {
            throw new IllegalStateException("종료된 대화에서는 턴을 생성할 수 없습니다.");
        }

        validateRequestId(requestId);

        return CompanionTurn.builder()
                .conversation(conversation)
                .requestId(requestId)
                .status(TurnStatus.RECEIVED)
                .build();
    }

    private static void validateRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException(
                    "요청 ID는 비워둘 수 없습니다."
            );
        }

        if (requestId.length() != 36) {
            throw new IllegalArgumentException(
                    "요청 ID는 UUID 문자열이어야 합니다."
            );
        }

        try {
            UUID.fromString(requestId);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "요청 ID는 유효한 UUID여야 합니다.",
                    exception
            );
        }
    }

    private void requiredStatus(TurnStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("예상한 턴 상태는 " + expected + "였지만 실제 상태는 " + status + " 입니다.");
        }
    }

    public void markTranscribed(
            String provider,
            String model
    ) {
        requiredStatus(TurnStatus.RECEIVED);

        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("STT 제공자 정보는 비어 있을 수 없습니다.");
        }

        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("STT 모델 정보는 비어 있을 수 없습니다.");
        }

        sttProvider = provider;
        sttModel = model;
        failureStage = null;
        status = TurnStatus.TRANSCRIBED;
    }

    public void markSafetyResult(
            SafetyType type,
            String ruleId
    ) {
        requiredStatus(TurnStatus.TRANSCRIBED);

        if (type == null) {
            throw new IllegalArgumentException("안전 검사 결과 유형은 비어 있을 수 없습니다.");
        }

        safetyType = type;
        safetyRuleId = ruleId;
    }

    public void markResponseGenerated(
            String provider,
            String model,
            String promptVersion,
            Integer inputTokens,
            Integer outputTokens
    ) {
        requiredStatus(TurnStatus.TRANSCRIBED);

        if (safetyType == null) {
            throw new IllegalStateException("응답을 생성하기 전에 안전 검사가 먼저 완료되어야 합니다.");
        }

        llmProvider = provider;
        llmModel = model;
        this.promptVersion = promptVersion;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        failureStage = null;
        status = TurnStatus.RESPONSE_GENERATED;
    }

    public void markCompleted(
            String ttsProvider,
            String ttsVoice
    ) {
        requiredStatus(TurnStatus.RESPONSE_GENERATED);

        if (ttsProvider == null || ttsProvider.isBlank()) {
            throw new IllegalArgumentException("TTS 제공자 정보는 비어 있을 수 없습니다.");
        }

        this.ttsProvider = ttsProvider;
        this.ttsVoice = ttsVoice;
        failureStage = null;
        status = TurnStatus.COMPLETED;
    }

    public void markFailed(FailureStage stage) {
        if (stage == null) {
            throw new IllegalArgumentException(
                    "실패 단계는 null일 수 없습니다."
            );
        }

        if (status == TurnStatus.COMPLETED) {
            throw new IllegalStateException(
                    "완료된 턴은 실패 상태로 변경할 수 없습니다."
            );
        }

        if (status == TurnStatus.FAILED) {
            throw new IllegalStateException(
                    "이미 실패 처리된 턴입니다."
            );
        }

        failureStage = stage;
        status = TurnStatus.FAILED;
    }

    public void prepareRetry() {
        requiredStatus(TurnStatus.FAILED);

        if (failureStage == null) {
            throw new IllegalStateException(
                    "실패 단계가 기록되지 않은 턴은 "
                            + "재시도할 수 없습니다."
            );
        }

        status = switch (failureStage) {
            case STT ->
                    TurnStatus.RECEIVED;

            case SAFETY, LLM ->
                    TurnStatus.TRANSCRIBED;

            case TTS ->
                    TurnStatus.RESPONSE_GENERATED;

            case PERSISTENCE ->
                    throw new IllegalStateException(
                            "저장 실패 턴은 "
                                    + "자동 재시도할 수 없습니다."
                    );
        };

        failureStage = null;
    }
}
