package com.example.senioron.domain.companion.controller;

import com.example.senioron.domain.companion.dto.response.CompanionVoiceTurnResponse;
import com.example.senioron.domain.companion.entity.FailureStage;
import com.example.senioron.domain.companion.entity.SafetyType;
import com.example.senioron.domain.companion.entity.TurnStatus;
import com.example.senioron.domain.companion.service.CompanionVoiceTurnService;
import com.example.senioron.domain.companion.service.model.CompanionVoiceTurnResult;
import com.example.senioron.domain.companion.service.model.VoiceTurnOutcome;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CompanionVoiceControllerTest {

    private final CompanionVoiceTurnService
            voiceTurnService =
            mock(CompanionVoiceTurnService.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CompanionVoiceController controller =
                new CompanionVoiceController(
                        voiceTurnService
                );

        mockMvc =
                MockMvcBuilders
                        .standaloneSetup(controller)
                        .setCustomArgumentResolvers(
                                new AuthenticationPrincipalArgumentResolver()
                        )
                        .build();
    }

    @Test
    void returnsSuccessfulBase64Audio()
            throws Exception {

        UUID requestId =
                UUID.randomUUID();

        CompanionVoiceTurnResponse response =
                response(
                        TurnStatus.COMPLETED,
                        VoiceTurnOutcome.SUCCESS,
                        null,
                        new byte[]{1, 2, 3}
                );

        given(
                voiceTurnService.process(
                        nullable(
                                com.example.senioron
                                        .domain.user.entity.User.class
                        ),
                        eq(10L),
                        eq(requestId),
                        any(MultipartFile.class)
                )
        ).willReturn(
                CompanionVoiceTurnResult
                        .success(response)
        );

        mockMvc.perform(
                        multipart(
                                "/api/companion/conversations/"
                                        + "10/voice-turn"
                        )
                                .file(audio())
                                .param(
                                        "requestId",
                                        requestId.toString()
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.code")
                                .value("COMMON_200")
                )
                .andExpect(
                        jsonPath(
                                "$.data.turnStatus"
                        ).value("COMPLETED")
                )
                .andExpect(
                        jsonPath(
                                "$.data.audioBase64"
                        ).value("AQID")
                );
    }

    @Test
    void returnsAcceptedForProcessingRequest()
            throws Exception {

        UUID requestId =
                UUID.randomUUID();

        CompanionVoiceTurnResponse response =
                response(
                        TurnStatus.TRANSCRIBED,
                        VoiceTurnOutcome.PROCESSING,
                        null,
                        null
                );

        given(
                voiceTurnService.process(
                        nullable(
                                com.example.senioron
                                        .domain.user.entity.User.class
                        ),
                        eq(10L),
                        eq(requestId),
                        any(MultipartFile.class)
                )
        ).willReturn(
                CompanionVoiceTurnResult
                        .processing(response)
        );

        mockMvc.perform(
                        multipart(
                                "/api/companion/conversations/"
                                        + "10/voice-turn"
                        )
                                .file(audio())
                                .param(
                                        "requestId",
                                        requestId.toString()
                                )
                )
                .andExpect(
                        status().isAccepted()
                )
                .andExpect(
                        jsonPath("$.code")
                                .value("COMMON_202")
                )
                .andExpect(
                        jsonPath(
                                "$.data.outcome"
                        ).value("PROCESSING")
                );
    }

    @Test
    void returnsActualTtsTimeoutStatus()
            throws Exception {

        UUID requestId =
                UUID.randomUUID();

        CompanionVoiceTurnResponse response =
                response(
                        TurnStatus.FAILED,
                        VoiceTurnOutcome.FALLBACK,
                        FailureStage.TTS,
                        null
                );

        given(
                voiceTurnService.process(
                        nullable(
                                com.example.senioron
                                        .domain.user.entity.User.class
                        ),
                        eq(10L),
                        eq(requestId),
                        any(MultipartFile.class)
                )
        ).willReturn(
                CompanionVoiceTurnResult
                        .failure(
                                ErrorCode
                                        .COMPANION_TTS_TIMEOUT,
                                response
                        )
        );

        mockMvc.perform(
                        multipart(
                                "/api/companion/conversations/"
                                        + "10/voice-turn"
                        )
                                .file(audio())
                                .param(
                                        "requestId",
                                        requestId.toString()
                                )
                )
                .andExpect(
                        status()
                                .isGatewayTimeout()
                )
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "COMPANION504_3"
                                )
                )
                .andExpect(
                        jsonPath(
                                "$.data.failureStage"
                        ).value("TTS")
                );
    }

    private MockMultipartFile audio() {
        return new MockMultipartFile(
                "audio",
                "voice.mp3",
                "audio/mpeg",
                new byte[]{1, 2, 3}
        );
    }

    private CompanionVoiceTurnResponse response(
            TurnStatus turnStatus,
            VoiceTurnOutcome outcome,
            FailureStage failureStage,
            byte[] audio
    ) {
        return new CompanionVoiceTurnResponse(
                10L,
                20L,
                turnStatus,
                outcome,
                failureStage,
                "안녕하세요.",
                "반가워요.",
                SafetyType.NORMAL,
                audio == null
                        ? null
                        : "audio/mpeg",
                audio == null
                        ? null
                        : "mp3",
                audio == null
                        ? null
                        : Base64.getEncoder()
                        .encodeToString(audio)
        );
    }
}
