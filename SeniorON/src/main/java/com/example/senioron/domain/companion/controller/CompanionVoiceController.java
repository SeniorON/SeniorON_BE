package com.example.senioron.domain.companion.controller;

import com.example.senioron.domain.companion.dto.response.CompanionVoiceTurnResponse;
import com.example.senioron.domain.companion.service.CompanionVoiceTurnService;
import com.example.senioron.domain.companion.service.model.CompanionVoiceTurnResult;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.global.apiPayload.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Tag(
        name = "말벗 음성 대화",
        description = "말벗 음성 한 턴 처리 API"
)
@RestController
@RequiredArgsConstructor
@RequestMapping(
        "/api/companion/conversations"
)
public class CompanionVoiceController {

    private final CompanionVoiceTurnService
            voiceTurnService;

    @Operation(
            summary = "말벗 음성 한 턴 처리",
            description =
                    "녹음 파일을 STT, 안전 검사, "
                            + "LLM, TTS 순서로 처리합니다."
    )
    @PostMapping(
            value =
                    "/{conversationId}/voice-turn",
            consumes =
                    MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<
            Response<CompanionVoiceTurnResponse>>
    processVoiceTurn(
            @AuthenticationPrincipal
            User user,

            @PathVariable
            Long conversationId,

            @RequestParam
            UUID requestId,

            @RequestPart(
                    value = "audio",
                    required = false
            )
            MultipartFile audio
    ) {
        CompanionVoiceTurnResult result =
                voiceTurnService.process(
                        user,
                        conversationId,
                        requestId,
                        audio
                );

        Response<CompanionVoiceTurnResponse>
                body =
                result.success()
                        ? Response.of(
                        result.code(),
                        result.response()
                )
                        : Response.fail(
                        result.code(),
                        result.response()
                );

        return ResponseEntity
                .status(
                        result.code()
                                .getStatus()
                )
                .body(body);
    }
}