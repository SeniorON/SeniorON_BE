package com.example.senioron.domain.companion.client.stt;

import com.example.senioron.domain.companion.client.stt.dto.OpenAiTranscriptionResponse;
import com.example.senioron.domain.companion.config.OpenAiSttProperties;
import com.example.senioron.domain.companion.service.model.TranscriptionResult;
import com.example.senioron.domain.companion.service.model.VoiceAudio;
import com.example.senioron.domain.companion.service.port.SpeechToTextPort;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
@Slf4j
public class OpenAiSpeechToTextClient
        implements SpeechToTextPort {

    private static final String PROVIDER = "OPENAI";

    private final RestClient restClient;
    private final OpenAiSttProperties properties;

    public OpenAiSpeechToTextClient(
            @Qualifier("openAiSttRestClient")
            RestClient restClient,
            OpenAiSttProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public TranscriptionResult transcribe(
            VoiceAudio audio
    ) {
        validateConfiguration();

        OpenAiTranscriptionResponse response = requestTranscription(audio);

        String text = extractText(response);

        return new TranscriptionResult(
                text,
                PROVIDER,
                properties.getModel()
        );
    }

    private void validateConfiguration() {
        if (properties.getApiKey() == null
                || properties.getApiKey().isBlank()
                || properties.getModel() == null
                || properties.getModel().isBlank()) {
            throw new BusinessException(ErrorCode.COMPANION_STT_NOT_CONFIGURED);
        }
    }

    private OpenAiTranscriptionResponse requestTranscription(
            VoiceAudio audio
    ) {
        try {
            MultipartBodyBuilder body =
                    new MultipartBodyBuilder();

            ByteArrayResource fileResource =
                    new ByteArrayResource(audio.bytes()) {
                        @Override
                        public String getFilename() {
                            return audio.filename();
                        }
                    };

            body.part("file", fileResource)
                    .contentType(
                            MediaType.parseMediaType(
                                    audio.contentType()
                            )
                    );

            body.part("model", properties.getModel());
            body.part("language", properties.getLanguage());
            body.part("response_format", "json");

            return restClient.post()
                    .uri("/v1/audio/transcriptions")
                    .headers(headers ->
                            headers.setBearerAuth(
                                    properties.getApiKey()
                            )
                    )
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body.build())
                    .retrieve()
                    .body(OpenAiTranscriptionResponse.class);

        } catch (InvalidMediaTypeException exception) {
            throw new BusinessException(ErrorCode.COMPANION_AUDIO_FORMAT_UNSUPPORTED);
        } catch (RestClientResponseException exception) {
            logProviderFailure(exception);
            throw new BusinessException(ErrorCode.COMPANION_STT_UNAVAILABLE);
        } catch (ResourceAccessException exception) {
            if (hasTimeoutCause(exception)) {
                throw new BusinessException(ErrorCode.COMPANION_STT_TIMEOUT);
            }

            throw new BusinessException(ErrorCode.COMPANION_STT_UNAVAILABLE);
        } catch (RestClientException exception) {
            throw new BusinessException(ErrorCode.COMPANION_STT_UNAVAILABLE);
        }
    }



    private String extractText(
            OpenAiTranscriptionResponse response
    ) {
        if (response == null
                || response.text() == null
                || response.text().isBlank()) {
            throw new BusinessException(ErrorCode.COMPANION_SPEECH_NOT_RECOGNIZED);
        }

        return response.text().trim();
    }

    private void logProviderFailure(
            RestClientResponseException exception
    ) {
        String requestId = exception.getResponseHeaders()
                == null
                ? null
                : exception.getResponseHeaders()
                .getFirst("x-request-id");

        log.warn(
                "OpenAI STT request failed: status={}, requestId={}",
                exception.getStatusCode(),
                requestId
        );
    }

    private boolean hasTimeoutCause(Throwable throwable) {
        Throwable cause = throwable;

        while (cause != null) {
            if (cause instanceof java.net.SocketTimeoutException
                    || cause instanceof java.net.http.HttpTimeoutException) {
                return true;
            }

            cause = cause.getCause();
        }

        return false;
    }
}