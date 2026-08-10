package com.example.senioron.domain.companion.service;

import com.example.senioron.domain.companion.dto.response.CompanionVoiceTurnResponse;
import com.example.senioron.domain.companion.entity.FailureStage;
import com.example.senioron.domain.companion.entity.SafetyType;
import com.example.senioron.domain.companion.entity.TurnStatus;
import com.example.senioron.domain.companion.service.model.CompanionReplyResult;
import com.example.senioron.domain.companion.service.model.CompanionTurnSnapshot;
import com.example.senioron.domain.companion.service.model.CompanionVoiceTurnResult;
import com.example.senioron.domain.companion.service.model.SafetyDecision;
import com.example.senioron.domain.companion.service.model.SynthesizedAudio;
import com.example.senioron.domain.companion.service.model.TranscriptionResult;
import com.example.senioron.domain.companion.service.model.TurnClaimResult;
import com.example.senioron.domain.companion.service.model.TurnClaimStatus;
import com.example.senioron.domain.companion.service.model.VoiceAudio;
import com.example.senioron.domain.companion.service.model.VoiceTurnOutcome;
import com.example.senioron.domain.companion.service.port.SpeechSynthesisPort;
import com.example.senioron.domain.companion.service.port.SpeechToTextPort;
import com.example.senioron.domain.companion.service.validation.VoiceFileValidator;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanionVoiceTurnService {

    private static final String EMERGENCY_RESPONSE =
            "위급한 상황으로 판단했어요. "
                    + "가까운 가족에게 알렸어요. "
                    + "숨쉬기 어렵거나 심하게 아프면 "
                    + "즉시 119에 연락해 주세요.";

    private static final String STT_FALLBACK_RESPONSE =
            "음성을 잘 듣지 못했어요. "
                    + "다시 한 번 말씀해 주세요.";

    private static final String SAFETY_FALLBACK_RESPONSE =
            "지금은 안전하게 답변하기 어려워요. "
                    + "잠시 후 다시 말씀해 주세요.";

    private static final String LLM_FALLBACK_RESPONSE =
            "지금은 답변을 드리기 어려워요. "
                    + "잠시 후 다시 시도해 주세요.";

    private final CompanionConversationService
            conversationService;

    private final CompanionTurnClaimTransactionService
            turnClaimTransactionService;

    private final CompanionPersistenceService
            persistenceService;

    private final VoiceFileValidator
            voiceFileValidator;

    private final SpeechToTextPort
            speechToTextPort;

    private final CompanionSafetyService
            safetyService;

    private final CompanionReplyService
            replyService;

    private final SpeechSynthesisPort
            speechSynthesisPort;

    public CompanionVoiceTurnResult process(
            User principal,
            Long conversationId,
            UUID requestId,
            MultipartFile audioFile
    ) {
        Long userId =
                conversationService
                        .getOwnedActiveUserId(
                                principal,
                                conversationId
                        );

        VoiceAudio voiceAudio =
                voiceFileValidator
                        .validateAndConvert(
                                audioFile
                        );

        TurnClaimResult claim =
                turnClaimTransactionService.claim(
                        conversationId,
                        userId,
                        requestId.toString()
                );

        if (claim.status()
                == TurnClaimStatus.PROCESSING) {

            return CompanionVoiceTurnResult
                    .processing(
                            createResponse(
                                    conversationId,
                                    claim.turnId(),
                                    claim.turnStatus(),
                                    VoiceTurnOutcome
                                            .PROCESSING,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null
                            )
                    );
        }

        CompanionTurnSnapshot snapshot = null;

        if (claim.status()
                != TurnClaimStatus.CREATED) {

            try {
                snapshot =
                        persistenceService
                                .loadTurnSnapshot(
                                        claim.turnId()
                                );
            } catch (RuntimeException exception) {
                log.warn(
                        "[COMPANION_VOICE_TURN] snapshot load failed turnId={} stage=PERSISTENCE",
                        claim.turnId(),
                        exception
                );

                return persistenceFailure(
                        conversationId,
                        claim.turnId(),
                        claim.turnStatus(),
                        null,
                        null,
                        null
                );
            }
        }

        if (claim.status()
                == TurnClaimStatus.COMPLETED) {

            return replayCompleted(
                    conversationId,
                    claim.turnId(),
                    snapshot
            );
        }

        ResumePlan resumePlan =
                resolveResumePlan(claim);

        String transcript =
                snapshot == null
                        ? null
                        : snapshot.transcript();

        String assistantText =
                snapshot == null
                        ? null
                        : snapshot.assistantText();

        SafetyType safetyType =
                snapshot == null
                        ? null
                        : snapshot.safetyType();

        if (resumePlan.runStt()) {

            TranscriptionResult transcription;

            try {
                transcription =
                        speechToTextPort
                                .transcribe(
                                        voiceAudio
                                );
            } catch (BusinessException exception) {
                safeMarkFailed(
                        claim.turnId(),
                        FailureStage.STT
                );

                SynthesizedAudio fallbackAudio =
                        trySynthesizeFallback(
                                claim.turnId(),
                                STT_FALLBACK_RESPONSE
                        );

                return CompanionVoiceTurnResult
                        .failure(
                                getErrorCode(exception),
                                createResponse(
                                        conversationId,
                                        claim.turnId(),
                                        TurnStatus.FAILED,
                                        VoiceTurnOutcome
                                                .FALLBACK,
                                        FailureStage.STT,
                                        null,
                                        STT_FALLBACK_RESPONSE,
                                        null,
                                        fallbackAudio
                                )
                        );
            }

            transcript =
                    transcription.text();

            try {
                persistenceService
                        .saveTranscription(
                                claim.turnId(),
                                transcription
                        );
            } catch (RuntimeException exception) {
                log.warn(
                        "[COMPANION_VOICE_TURN] transcription persistence failed turnId={} stage=PERSISTENCE",
                        claim.turnId(),
                        exception
                );

                return persistenceFailure(
                        conversationId,
                        claim.turnId(),
                        TurnStatus.RECEIVED,
                        transcript,
                        null,
                        null
                );
            }
        }

        if ((resumePlan.runSafety()
                || resumePlan.runResponseGeneration())
                && (transcript == null
                || transcript.isBlank())) {

            return persistenceFailure(
                    conversationId,
                    claim.turnId(),
                    claim.turnStatus(),
                    null,
                    assistantText,
                    safetyType
            );
        }

        if (resumePlan.runResponseGeneration()) {

            if (resumePlan.runSafety()) {

                SafetyDecision decision;

                try {
                    decision =
                            safetyService
                                    .checkAndNotify(
                                            claim.turnId(),
                                            conversationId,
                                            userId,
                                            transcript
                                    );
                } catch (BusinessException exception) {
                    safeMarkFailed(
                            claim.turnId(),
                            FailureStage.SAFETY
                    );

                    return CompanionVoiceTurnResult
                            .failure(
                                    getErrorCode(
                                            exception
                                    ),
                                    createResponse(
                                            conversationId,
                                            claim.turnId(),
                                            TurnStatus.FAILED,
                                            VoiceTurnOutcome
                                                    .FALLBACK,
                                            FailureStage
                                                    .SAFETY,
                                            transcript,
                                            SAFETY_FALLBACK_RESPONSE,
                                            null,
                                            null
                                    )
                            );
                }

                safetyType =
                        decision.type();
            }

            if (safetyType == null) {
                return persistenceFailure(
                        conversationId,
                        claim.turnId(),
                        TurnStatus.TRANSCRIBED,
                        transcript,
                        null,
                        null
                );
            }

            String llmProvider = null;
            String llmModel = null;
            String promptVersion = null;
            Integer inputTokens = null;
            Integer outputTokens = null;

            if (safetyType
                    == SafetyType.EMERGENCY) {

                assistantText =
                        EMERGENCY_RESPONSE;

            } else {
                CompanionReplyResult reply;

                try {
                    reply =
                            replyService.generate(
                                    principal,
                                    conversationId
                            );
                } catch (BusinessException exception) {
                    safeMarkFailed(
                            claim.turnId(),
                            FailureStage.LLM
                    );

                    return CompanionVoiceTurnResult
                            .failure(
                                    getErrorCode(
                                            exception
                                    ),
                                    createResponse(
                                            conversationId,
                                            claim.turnId(),
                                            TurnStatus.FAILED,
                                            VoiceTurnOutcome
                                                    .FALLBACK,
                                            FailureStage.LLM,
                                            transcript,
                                            LLM_FALLBACK_RESPONSE,
                                            safetyType,
                                            null
                                    )
                            );
                }

                assistantText =
                        reply.text();

                llmProvider =
                        reply.provider();

                llmModel =
                        reply.model();

                promptVersion =
                        reply.promptVersion();

                inputTokens =
                        reply.inputTokens();

                outputTokens =
                        reply.outputTokens();
            }

            try {
                persistenceService
                        .saveGeneratedResponse(
                                claim.turnId(),
                                assistantText,
                                llmProvider,
                                llmModel,
                                promptVersion,
                                inputTokens,
                                outputTokens
                        );
            } catch (RuntimeException exception) {
                log.warn(
                        "[COMPANION_VOICE_TURN] response persistence failed turnId={} stage=PERSISTENCE",
                        claim.turnId(),
                        exception
                );

                return persistenceFailure(
                        conversationId,
                        claim.turnId(),
                        TurnStatus.TRANSCRIBED,
                        transcript,
                        assistantText,
                        safetyType
                );
            }
        }

        if (assistantText == null
                || assistantText.isBlank()) {

            return persistenceFailure(
                    conversationId,
                    claim.turnId(),
                    claim.turnStatus(),
                    transcript,
                    null,
                    safetyType
            );
        }

        SynthesizedAudio synthesizedAudio;

        try {
            synthesizedAudio =
                    speechSynthesisPort
                            .synthesize(
                                    assistantText
                            );
        } catch (BusinessException exception) {
            safeMarkFailed(
                    claim.turnId(),
                    FailureStage.TTS
            );

            return CompanionVoiceTurnResult
                    .failure(
                            getErrorCode(exception),
                            createResponse(
                                    conversationId,
                                    claim.turnId(),
                                    TurnStatus.FAILED,
                                    VoiceTurnOutcome
                                            .FALLBACK,
                                    FailureStage.TTS,
                                    transcript,
                                    assistantText,
                                    safetyType,
                                    null
                            )
                    );
        }

        try {
            persistenceService
                    .markCompleted(
                            claim.turnId(),
                            synthesizedAudio
                                    .provider(),
                            synthesizedAudio
                                    .voice()
                    );
        } catch (RuntimeException exception) {
            log.warn(
                    "[COMPANION_VOICE_TURN] completion persistence failed turnId={} stage=PERSISTENCE",
                    claim.turnId(),
                    exception
            );

            return persistenceFailure(
                    conversationId,
                    claim.turnId(),
                    TurnStatus.RESPONSE_GENERATED,
                    transcript,
                    assistantText,
                    safetyType
            );
        }

        return CompanionVoiceTurnResult
                .success(
                        createResponse(
                                conversationId,
                                claim.turnId(),
                                TurnStatus.COMPLETED,
                                VoiceTurnOutcome.SUCCESS,
                                null,
                                transcript,
                                assistantText,
                                safetyType,
                                synthesizedAudio
                        )
                );
    }

    private CompanionVoiceTurnResult
    replayCompleted(
            Long conversationId,
            Long turnId,
            CompanionTurnSnapshot snapshot
    ) {
        if (snapshot == null
                || snapshot.assistantText() == null
                || snapshot.assistantText()
                .isBlank()) {

            return persistenceFailure(
                    conversationId,
                    turnId,
                    TurnStatus.COMPLETED,
                    snapshot == null
                            ? null
                            : snapshot.transcript(),
                    null,
                    snapshot == null
                            ? null
                            : snapshot.safetyType()
            );
        }

        try {
            SynthesizedAudio synthesizedAudio =
                    speechSynthesisPort
                            .synthesize(
                                    snapshot
                                            .assistantText()
                            );

            return CompanionVoiceTurnResult
                    .success(
                            createResponse(
                                    conversationId,
                                    turnId,
                                    TurnStatus.COMPLETED,
                                    VoiceTurnOutcome
                                            .SUCCESS,
                                    null,
                                    snapshot.transcript(),
                                    snapshot
                                            .assistantText(),
                                    snapshot.safetyType(),
                                    synthesizedAudio
                            )
                    );

        } catch (BusinessException exception) {
            /*
             * 이미 완료된 턴은 재합성에 실패해도
             * FAILED로 변경하지 않는다.
             */
            return CompanionVoiceTurnResult
                    .failure(
                            getErrorCode(exception),
                            createResponse(
                                    conversationId,
                                    turnId,
                                    TurnStatus.COMPLETED,
                                    VoiceTurnOutcome
                                            .FALLBACK,
                                    FailureStage.TTS,
                                    snapshot.transcript(),
                                    snapshot
                                            .assistantText(),
                                    snapshot.safetyType(),
                                    null
                            )
                    );
        }
    }

    private CompanionVoiceTurnResult
    persistenceFailure(
            Long conversationId,
            Long turnId,
            TurnStatus previousStatus,
            String transcript,
            String assistantText,
            SafetyType safetyType
    ) {
        if (previousStatus
                != TurnStatus.COMPLETED) {

            safeMarkFailed(
                    turnId,
                    FailureStage.PERSISTENCE
            );
        }

        TurnStatus responseStatus =
                previousStatus
                        == TurnStatus.COMPLETED
                        ? TurnStatus.COMPLETED
                        : TurnStatus.FAILED;

        return CompanionVoiceTurnResult
                .failure(
                        ErrorCode
                                .COMPANION_PERSISTENCE_FAILED,
                        createResponse(
                                conversationId,
                                turnId,
                                responseStatus,
                                VoiceTurnOutcome.FALLBACK,
                                FailureStage.PERSISTENCE,
                                transcript,
                                assistantText,
                                safetyType,
                                null
                        )
                );
    }

    private void safeMarkFailed(
            Long turnId,
            FailureStage failureStage
    ) {
        try {
            persistenceService.markFailed(
                    turnId,
                    failureStage
            );
        } catch (RuntimeException exception) {
            log.error(
                    "[COMPANION_VOICE_TURN] failed to persist failure state turnId={} failureStage={}",
                    turnId,
                    failureStage,
                    exception
            );
        }
    }

    private SynthesizedAudio
    trySynthesizeFallback(
            Long turnId,
            String text
    ) {
        try {
            return speechSynthesisPort
                    .synthesize(text);
        } catch (RuntimeException exception) {
            log.warn(
                    "[COMPANION_VOICE_TURN] fallback TTS failed turnId={} originalStage=STT",
                    turnId,
                    exception
            );

            return null;
        }
    }

    private ErrorCode getErrorCode(
            BusinessException exception
    ) {
        if (exception.getCode()
                instanceof ErrorCode errorCode) {

            return errorCode;
        }

        return ErrorCode.INTERNAL_SERVER_ERROR;
    }

    private CompanionVoiceTurnResponse
    createResponse(
            Long conversationId,
            Long turnId,
            TurnStatus turnStatus,
            VoiceTurnOutcome outcome,
            FailureStage failureStage,
            String transcript,
            String assistantText,
            SafetyType safetyType,
            SynthesizedAudio audio
    ) {
        return new CompanionVoiceTurnResponse(
                conversationId,
                turnId,
                turnStatus,
                outcome,
                failureStage,
                transcript,
                assistantText,
                safetyType,
                audio == null
                        ? null
                        : audio.contentType(),
                audio == null
                        ? null
                        : audio.format(),
                audio == null
                        ? null
                        : Base64.getEncoder()
                        .encodeToString(
                                audio.bytes()
                        )
        );
    }

    private record ResumePlan(
            boolean runStt,
            boolean runSafety,
            boolean runResponseGeneration
    ) {
    }

    private ResumePlan resolveResumePlan(
            TurnClaimResult claim
    ) {
        if (claim.status()
                == TurnClaimStatus.CREATED) {

            return new ResumePlan(
                    true,
                    true,
                    true
            );
        }

        if (claim.status()
                != TurnClaimStatus.RETRY
                || claim.retryStage() == null) {

            throw new IllegalStateException(
                    "재시도 단계가 올바르지 않습니다."
            );
        }

        return switch (claim.retryStage()) {
            case STT ->
                    new ResumePlan(
                            true,
                            true,
                            true
                    );

            case SAFETY ->
                    new ResumePlan(
                            false,
                            true,
                            true
                    );

            case LLM ->
                    new ResumePlan(
                            false,
                            false,
                            true
                    );

            case TTS ->
                    new ResumePlan(
                            false,
                            false,
                            false
                    );

            case PERSISTENCE ->
                    throw new IllegalStateException(
                            "저장 실패는 자동 재시도할 수 없습니다."
                    );
        };
    }
}
