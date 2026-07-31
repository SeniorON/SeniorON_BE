package com.example.senioron.domain.companion.service.validation;

import com.example.senioron.domain.companion.service.model.VoiceAudio;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VoiceFileValidatorTest {

    private final VoiceFileValidator validator =
            new VoiceFileValidator();

    @Test
    void 정상_M4A_파일을_검증하고_안전한_파일명으로_변환한다() {
        MockMultipartFile file = new MockMultipartFile(
                "audio",
                "C:\\fakepath\\voice.m4a",
                "audio/mp4",
                new byte[]{1, 2, 3}
        );

        VoiceAudio result =
                validator.validateAndConvert(file);

        assertThat(result.filename())
                .isEqualTo("voice.m4a");
        assertThat(result.contentType())
                .isEqualTo("audio/mp4");
        assertThat(result.bytes())
                .containsExactly(1, 2, 3);
    }

    @Test
    void 파일이_null이거나_비어있으면_거부한다() {
        assertError(
                () -> validator.validateAndConvert(null),
                ErrorCode.COMPANION_AUDIO_REQUIRED
        );

        MockMultipartFile emptyFile =
                new MockMultipartFile(
                        "audio",
                        "voice.mp3",
                        "audio/mpeg",
                        new byte[0]
                );

        assertError(
                () -> validator.validateAndConvert(emptyFile),
                ErrorCode.COMPANION_AUDIO_REQUIRED
        );
    }

    @Test
    void 파일이_10MB를_초과하면_거부한다() {
        MockMultipartFile file =
                new MockMultipartFile(
                        "audio",
                        "voice.mp3",
                        "audio/mpeg",
                        new byte[
                                10 * 1024 * 1024 + 1
                                ]
                );

        assertError(
                () -> validator.validateAndConvert(file),
                ErrorCode.COMPANION_AUDIO_SIZE_EXCEEDED
        );
    }

    @Test
    void 잘못된_확장자나_MIME_타입을_거부한다() {
        MockMultipartFile invalidExtension =
                new MockMultipartFile(
                        "audio",
                        "voice.exe",
                        "audio/mpeg",
                        new byte[]{1}
                );

        MockMultipartFile invalidContentType =
                new MockMultipartFile(
                        "audio",
                        "voice.mp3",
                        "application/octet-stream",
                        new byte[]{1}
                );

        assertError(
                () -> validator.validateAndConvert(
                        invalidExtension
                ),
                ErrorCode
                        .COMPANION_AUDIO_FORMAT_UNSUPPORTED
        );

        assertError(
                () -> validator.validateAndConvert(
                        invalidContentType
                ),
                ErrorCode
                        .COMPANION_AUDIO_FORMAT_UNSUPPORTED
        );
    }

    private void assertError(
            Executable executable,
            ErrorCode expected
    ) {
        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        executable
                );

        assertThat(exception.getCode())
                .isEqualTo(expected);
    }
}