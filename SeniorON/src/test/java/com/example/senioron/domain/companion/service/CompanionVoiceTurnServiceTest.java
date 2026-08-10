package com.example.senioron.domain.companion.service;

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
import com.example.senioron.global.apiPayload.code.ResultCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CompanionVoiceTurnServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long CONVERSATION_ID = 10L;
    private static final Long TURN_ID = 20L;

    private final CompanionConversationService
            conversationService =
            mock(CompanionConversationService.class);

    private final CompanionTurnClaimTransactionService
            claimService =
            mock(
                    CompanionTurnClaimTransactionService.class
            );

    private final CompanionPersistenceService
            persistenceService =
            mock(CompanionPersistenceService.class);

    private final VoiceFileValidator
            voiceFileValidator =
            mock(VoiceFileValidator.class);

    private final SpeechToTextPort
            speechToTextPort =
            mock(SpeechToTextPort.class);

    private final CompanionSafetyService
            safetyService =
            mock(CompanionSafetyService.class);

    private final CompanionReplyService
            replyService =
            mock(CompanionReplyService.class);

    private final SpeechSynthesisPort
            speechSynthesisPort =
            mock(SpeechSynthesisPort.class);

    private final User principal =
            mock(User.class);

    private final MultipartFile audioFile =
            mock(MultipartFile.class);

    private final VoiceAudio voiceAudio =
            new VoiceAudio(
                    "voice.mp3",
                    "audio/mpeg",
                    new byte[]{1, 2, 3}
            );

    private CompanionVoiceTurnService service;

    @BeforeEach
    void setUp() {
        service =
                new CompanionVoiceTurnService(
                        conversationService,
                        claimService,
                        persistenceService,
                        voiceFileValidator,
                        speechToTextPort,
                        safetyService,
                        replyService,
                        speechSynthesisPort
                );

        given(
                conversationService
                        .getOwnedActiveUserId(
                                principal,
                                CONVERSATION_ID
                        )
        ).willReturn(USER_ID);

        given(
                voiceFileValidator
                        .validateAndConvert(
                                audioFile
                        )
        ).willReturn(voiceAudio);
    }

    @Test
    void processesNormalVoiceTurn() {
        UUID requestId =
                UUID.randomUUID();

        givenCreatedClaim(requestId);

        TranscriptionResult transcription =
                new TranscriptionResult(
                        "오늘 공원에 다녀왔어요.",
                        "OPENAI",
                        "gpt-4o-mini-transcribe"
                );

        CompanionReplyResult reply =
                new CompanionReplyResult(
                        "공원에서 무엇이 가장 좋으셨어요?",
                        "ANTHROPIC",
                        "claude-haiku",
                        "companion-v1",
                        100,
                        30
                );

        SynthesizedAudio audio =
                synthesizedAudio();

        given(
                speechToTextPort
                        .transcribe(voiceAudio)
        ).willReturn(transcription);

        given(
                safetyService
                        .checkAndNotify(
                                TURN_ID,
                                CONVERSATION_ID,
                                USER_ID,
                                transcription.text()
                        )
        ).willReturn(
                SafetyDecision.normal()
        );

        given(
                replyService.generate(
                        principal,
                        CONVERSATION_ID
                )
        ).willReturn(reply);

        given(
                speechSynthesisPort
                        .synthesize(
                                reply.text()
                        )
        ).willReturn(audio);

        CompanionVoiceTurnResult result =
                service.process(
                        principal,
                        CONVERSATION_ID,
                        requestId,
                        audioFile
                );

        assertThat(result.code())
                .isEqualTo(ResultCode.OK);

        assertThat(result.response().turnStatus())
                .isEqualTo(TurnStatus.COMPLETED);

        assertThat(result.response().outcome())
                .isEqualTo(VoiceTurnOutcome.SUCCESS);

        assertThat(result.response().transcript())
                .isEqualTo(transcription.text());

        assertThat(result.response().assistantText())
                .isEqualTo(reply.text());

        assertThat(result.response().audioBase64())
                .containsExactly(1, 2, 3);

        verify(persistenceService)
                .saveTranscription(
                        TURN_ID,
                        transcription
                );

        verify(persistenceService)
                .saveGeneratedResponse(
                        TURN_ID,
                        reply.text(),
                        reply.provider(),
                        reply.model(),
                        reply.promptVersion(),
                        reply.inputTokens(),
                        reply.outputTokens()
                );

        verify(persistenceService)
                .markCompleted(
                        TURN_ID,
                        "GOOGLE_CLOUD",
                        "ko-KR-Neural2-A"
                );
    }

    @Test
    void emergencyTurnDoesNotCallLlm() {
        UUID requestId =
                UUID.randomUUID();

        givenCreatedClaim(requestId);

        TranscriptionResult transcription =
                new TranscriptionResult(
                        "숨을 못 쉬겠어요.",
                        "OPENAI",
                        "gpt-4o-mini-transcribe"
                );

        given(
                speechToTextPort
                        .transcribe(voiceAudio)
        ).willReturn(transcription);

        given(
                safetyService
                        .checkAndNotify(
                                TURN_ID,
                                CONVERSATION_ID,
                                USER_ID,
                                transcription.text()
                        )
        ).willReturn(
                SafetyDecision.emergency(
                        "PHYSICAL_BREATHING_001"
                )
        );

        given(
                speechSynthesisPort
                        .synthesize(any(String.class))
        ).willReturn(
                synthesizedAudio()
        );

        CompanionVoiceTurnResult result =
                service.process(
                        principal,
                        CONVERSATION_ID,
                        requestId,
                        audioFile
                );

        assertThat(result.response().safetyType())
                .isEqualTo(SafetyType.EMERGENCY);

        assertThat(result.response().assistantText())
                .contains("119");

        verify(
                replyService,
                never()
        ).generate(
                any(),
                any()
        );

        verify(persistenceService)
                .saveGeneratedResponse(
                        eq(TURN_ID),
                        contains("119"),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull()
                );
    }

    @Test
    void sameProcessingRequestReturnsAccepted() {
        UUID requestId =
                UUID.randomUUID();

        given(
                claimService.claim(
                        CONVERSATION_ID,
                        USER_ID,
                        requestId.toString()
                )
        ).willReturn(
                new TurnClaimResult(
                        TurnClaimStatus.PROCESSING,
                        TURN_ID,
                        TurnStatus.TRANSCRIBED,
                        null
                )
        );

        CompanionVoiceTurnResult result =
                service.process(
                        principal,
                        CONVERSATION_ID,
                        requestId,
                        audioFile
                );

        assertThat(result.code())
                .isEqualTo(ResultCode.ACCEPTED);

        assertThat(result.response().outcome())
                .isEqualTo(
                        VoiceTurnOutcome.PROCESSING
                );

        assertThat(result.response().turnStatus())
                .isEqualTo(
                        TurnStatus.TRANSCRIBED
                );

        verify(
                speechToTextPort,
                never()
        ).transcribe(any());

        verify(
                safetyService,
                never()
        ).checkAndNotify(
                any(),
                any(),
                any(),
                any()
        );

        verify(
                replyService,
                never()
        ).generate(
                any(),
                any()
        );

        verify(
                speechSynthesisPort,
                never()
        ).synthesize(any());
    }

    @Test
    void completedRequestOnlyRunsTtsAgain() {
        UUID requestId =
                UUID.randomUUID();

        given(
                claimService.claim(
                        CONVERSATION_ID,
                        USER_ID,
                        requestId.toString()
                )
        ).willReturn(
                new TurnClaimResult(
                        TurnClaimStatus.COMPLETED,
                        TURN_ID,
                        TurnStatus.COMPLETED,
                        null
                )
        );

        CompanionTurnSnapshot snapshot =
                new CompanionTurnSnapshot(
                        CONVERSATION_ID,
                        TURN_ID,
                        TurnStatus.COMPLETED,
                        null,
                        SafetyType.NORMAL,
                        "오늘 날씨가 좋아요.",
                        "산책하기 좋은 날이네요."
                );

        given(
                persistenceService
                        .loadTurnSnapshot(TURN_ID)
        ).willReturn(snapshot);

        given(
                speechSynthesisPort
                        .synthesize(
                                snapshot.assistantText()
                        )
        ).willReturn(
                synthesizedAudio()
        );

        CompanionVoiceTurnResult result =
                service.process(
                        principal,
                        CONVERSATION_ID,
                        requestId,
                        audioFile
                );

        assertThat(result.code())
                .isEqualTo(ResultCode.OK);

        assertThat(result.response().turnStatus())
                .isEqualTo(TurnStatus.COMPLETED);

        verify(
                speechToTextPort,
                never()
        ).transcribe(any());

        verify(
                safetyService,
                never()
        ).checkAndNotify(
                any(),
                any(),
                any(),
                any()
        );

        verify(
                replyService,
                never()
        ).generate(
                any(),
                any()
        );

        verify(
                persistenceService,
                never()
        ).markCompleted(
                any(),
                any(),
                any()
        );
    }

    @Test
    void ttsFailureMarksTtsStage() {
        UUID requestId =
                UUID.randomUUID();

        givenCreatedClaim(requestId);

        TranscriptionResult transcription =
                new TranscriptionResult(
                        "안녕하세요.",
                        "OPENAI",
                        "gpt-4o-mini-transcribe"
                );

        CompanionReplyResult reply =
                new CompanionReplyResult(
                        "안녕하세요. 반가워요.",
                        "ANTHROPIC",
                        "claude-haiku",
                        "companion-v1",
                        30,
                        10
                );

        given(
                speechToTextPort
                        .transcribe(voiceAudio)
        ).willReturn(transcription);

        given(
                safetyService
                        .checkAndNotify(
                                TURN_ID,
                                CONVERSATION_ID,
                                USER_ID,
                                transcription.text()
                        )
        ).willReturn(
                SafetyDecision.normal()
        );

        given(
                replyService.generate(
                        principal,
                        CONVERSATION_ID
                )
        ).willReturn(reply);

        given(
                speechSynthesisPort
                        .synthesize(
                                reply.text()
                        )
        ).willThrow(
                new BusinessException(
                        ErrorCode
                                .COMPANION_TTS_TIMEOUT
                )
        );

        CompanionVoiceTurnResult result =
                service.process(
                        principal,
                        CONVERSATION_ID,
                        requestId,
                        audioFile
                );

        assertThat(result.code())
                .isEqualTo(
                        ErrorCode
                                .COMPANION_TTS_TIMEOUT
                );

        assertThat(result.response().failureStage())
                .isEqualTo(FailureStage.TTS);

        assertThat(result.response().outcome())
                .isEqualTo(
                        VoiceTurnOutcome.FALLBACK
                );

        verify(persistenceService)
                .markFailed(
                        TURN_ID,
                        FailureStage.TTS
                );
    }

    @Test
    void ttsRetryDoesNotRepeatSafetyOrLlm() {
        UUID requestId =
                UUID.randomUUID();

        given(
                claimService.claim(
                        CONVERSATION_ID,
                        USER_ID,
                        requestId.toString()
                )
        ).willReturn(
                new TurnClaimResult(
                        TurnClaimStatus.RETRY,
                        TURN_ID,
                        TurnStatus.RESPONSE_GENERATED,
                        FailureStage.TTS
                )
        );

        CompanionTurnSnapshot snapshot =
                new CompanionTurnSnapshot(
                        CONVERSATION_ID,
                        TURN_ID,
                        TurnStatus.RESPONSE_GENERATED,
                        null,
                        SafetyType.EMERGENCY,
                        "숨을 못 쉬겠어요.",
                        "가족에게 알렸어요. 즉시 119에 연락해 주세요."
                );

        given(
                persistenceService
                        .loadTurnSnapshot(TURN_ID)
        ).willReturn(snapshot);

        given(
                speechSynthesisPort
                        .synthesize(
                                snapshot.assistantText()
                        )
        ).willReturn(
                synthesizedAudio()
        );

        CompanionVoiceTurnResult result =
                service.process(
                        principal,
                        CONVERSATION_ID,
                        requestId,
                        audioFile
                );

        assertThat(result.response().turnStatus())
                .isEqualTo(TurnStatus.COMPLETED);

        verify(
                safetyService,
                never()
        ).checkAndNotify(
                any(),
                any(),
                any(),
                any()
        );

        verify(
                replyService,
                never()
        ).generate(
                any(),
                any()
        );

        verify(persistenceService)
                .markCompleted(
                        TURN_ID,
                        "GOOGLE_CLOUD",
                        "ko-KR-Neural2-A"
                );
    }

    private void givenCreatedClaim(
            UUID requestId
    ) {
        given(
                claimService.claim(
                        CONVERSATION_ID,
                        USER_ID,
                        requestId.toString()
                )
        ).willReturn(
                new TurnClaimResult(
                        TurnClaimStatus.CREATED,
                        TURN_ID,
                        TurnStatus.RECEIVED,
                        null
                )
        );
    }

    private SynthesizedAudio
    synthesizedAudio() {
        return new SynthesizedAudio(
                new byte[]{1, 2, 3},
                "audio/mpeg",
                "mp3",
                "GOOGLE_CLOUD",
                "ko-KR-Neural2-A"
        );
    }
}