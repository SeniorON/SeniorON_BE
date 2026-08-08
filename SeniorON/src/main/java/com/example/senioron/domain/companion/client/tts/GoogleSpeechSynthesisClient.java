package com.example.senioron.domain.companion.client.tts;

import com.example.senioron.domain.companion.config.GoogleTtsProperties;
import com.example.senioron.domain.companion.service.model.SynthesizedAudio;
import com.example.senioron.domain.companion.service.port.SpeechSynthesisPort;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.StatusCode;
import com.google.cloud.texttospeech.v1.AudioConfig;
import com.google.cloud.texttospeech.v1.AudioEncoding;
import com.google.cloud.texttospeech.v1.SynthesisInput;
import com.google.cloud.texttospeech.v1.SynthesizeSpeechResponse;
import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1.VoiceSelectionParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@Slf4j
public class GoogleSpeechSynthesisClient
        implements SpeechSynthesisPort {

    private static final String PROVIDER =
            "GOOGLE_CLOUD";

    private static final String CONTENT_TYPE =
            "audio/mpeg";

    private static final String FORMAT =
            "mp3";

    private static final int MAX_INPUT_BYTES =
            5_000;

    private final ObjectProvider<TextToSpeechClient>
            clientProvider;

    private final GoogleTtsProperties properties;

    public GoogleSpeechSynthesisClient(
            ObjectProvider<TextToSpeechClient> clientProvider,
            GoogleTtsProperties properties
    ) {
        this.clientProvider = clientProvider;
        this.properties = properties;
    }

    @Override
    public SynthesizedAudio synthesize(String text) {
        validateText(text);
        validateConfiguration();

        SynthesisInput input =
                SynthesisInput.newBuilder()
                        .setText(text)
                        .build();

        VoiceSelectionParams voice =
                VoiceSelectionParams.newBuilder()
                        .setLanguageCode(properties.getLanguageCode())
                        .setName(properties.getVoiceName())
                        .build();

        AudioConfig audioConfig =
                AudioConfig.newBuilder()
                        .setAudioEncoding(AudioEncoding.MP3)
                        .setSpeakingRate(properties.getSpeakingRate())
                        .setPitch(properties.getPitch())
                        .setVolumeGainDb(properties.getVolumeGainDb())
                        .build();

        try {
            TextToSpeechClient client = obtainClient();

            SynthesizeSpeechResponse response =
                    client.synthesizeSpeech(
                            input,
                            voice,
                            audioConfig
                    );

            if (response == null
                    || response.getAudioContent().isEmpty()) {
                throw new BusinessException(ErrorCode.COMPANION_TTS_UNAVAILABLE);
            }

            return new SynthesizedAudio(
                    response.getAudioContent()
                            .toByteArray(),
                    CONTENT_TYPE,
                    FORMAT,
                    PROVIDER,
                    properties.getVoiceName()
            );

        } catch (ApiException exception) {
            throw convertApiException(exception);
        }
    }

    private TextToSpeechClient obtainClient() {
        try {
            return clientProvider.getObject();

        } catch (BeanCreationException exception) {
            if (hasCause(exception, IOException.class)) {
                throw new BusinessException(ErrorCode.COMPANION_TTS_NOT_CONFIGURED);
            }

            throw exception;
        }
    }

    private void validateText(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("TTS 입력 문장은 비어 있을 수 없습니다.");
        }

        int byteLength =
                text.getBytes(StandardCharsets.UTF_8)
                        .length;

        if (byteLength > MAX_INPUT_BYTES) {
            throw new IllegalArgumentException("TTS 입력은 UTF-8 기준 5,000바이트를 초과할 수 없습니다.");
        }
    }

    private void validateConfiguration() {
        if (properties.getLanguageCode() == null
                || properties.getLanguageCode().isBlank()
                || properties.getVoiceName() == null
                || properties.getVoiceName().isBlank()
                || properties.getAudioEncoding() == null
                || !properties.getAudioEncoding()
                .equalsIgnoreCase("MP3")) {
            throw new BusinessException(ErrorCode.COMPANION_TTS_NOT_CONFIGURED);
        }

        if (properties.getSpeakingRate() < 0.25
                || properties.getSpeakingRate() > 4.0
                || properties.getPitch() < -20.0
                || properties.getPitch() > 20.0
                || properties.getVolumeGainDb() < -96.0
                || properties.getVolumeGainDb() > 16.0) {
            throw new BusinessException(ErrorCode.COMPANION_TTS_NOT_CONFIGURED);
        }
    }

    private BusinessException convertApiException(
            ApiException exception
    ) {
        StatusCode statusCode =
                exception.getStatusCode();

        StatusCode.Code code =
                statusCode == null
                        ? StatusCode.Code.UNKNOWN
                        : statusCode.getCode();

        log.warn("Google Cloud TTS request failed: code={}", code
        );

        return switch (code) {
            case UNAUTHENTICATED,
                 PERMISSION_DENIED,
                 INVALID_ARGUMENT,
                 FAILED_PRECONDITION,
                 OUT_OF_RANGE -> new BusinessException(ErrorCode.COMPANION_TTS_NOT_CONFIGURED);

            case DEADLINE_EXCEEDED -> new BusinessException(ErrorCode.COMPANION_TTS_TIMEOUT);

            default -> new BusinessException(ErrorCode.COMPANION_TTS_UNAVAILABLE);
        };
    }

    private boolean hasCause(
            Throwable throwable,
            Class<? extends Throwable> causeType
    ) {
        Throwable cause = throwable;

        while (cause != null) {
            if (causeType.isInstance(cause)) {
                return true;
            }

            cause = cause.getCause();
        }

        return false;
    }
}