package com.example.senioron.domain.companion.client.stt;

import com.example.senioron.domain.companion.config.OpenAiSttProperties;
import com.example.senioron.domain.companion.service.model.TranscriptionResult;
import com.example.senioron.domain.companion.service.model.VoiceAudio;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiSpeechToTextClientTest {

    private static final String BASE_URL =
            "https://api.openai.com";

    private OpenAiSttProperties properties;
    private MockRestServiceServer server;
    private OpenAiSpeechToTextClient client;
    private VoiceAudio audio;

    @BeforeEach
    void setUp() {
        properties = new OpenAiSttProperties();
        properties.setBaseUrl(BASE_URL);
        properties.setApiKey("test-api-key");
        properties.setModel(
                "gpt-4o-mini-transcribe"
        );
        properties.setLanguage("ko");

        RestClient.Builder builder =
                RestClient.builder()
                        .baseUrl(BASE_URL);

        server = MockRestServiceServer
                .bindTo(builder)
                .build();

        client = new OpenAiSpeechToTextClient(
                builder.build(),
                properties
        );

        audio = new VoiceAudio(
                "voice.m4a",
                "audio/mp4",
                new byte[]{1, 2, 3}
        );
    }

    @Test
    void multipart_요청을_보내고_인식_결과를_반환한다() {
        server.expect(
                        requestTo(
                                BASE_URL
                                        + "/v1/audio/transcriptions"
                        )
                )
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer test-api-key"
                ))
                .andExpect(content()
                        .contentTypeCompatibleWith(
                                MediaType.MULTIPART_FORM_DATA
                        ))
                .andExpect(content().string(
                        containsString(
                                "name=\"file\""
                        )
                ))
                .andExpect(content().string(
                        containsString(
                                "filename=\"voice.m4a\""
                        )
                ))
                .andExpect(content().string(
                        containsString(
                                "name=\"model\""
                        )
                ))
                .andExpect(content().string(
                        containsString(
                                "gpt-4o-mini-transcribe"
                        )
                ))
                .andExpect(content().string(
                        containsString(
                                "name=\"language\""
                        )
                ))
                .andExpect(content().string(
                        containsString("ko")
                ))
                .andExpect(content().string(
                        containsString(
                                "name=\"response_format\""
                        )
                ))
                .andExpect(content().string(
                        containsString("json")
                ))
                .andRespond(withSuccess(
                        "{\"text\":\" 안녕하세요 \"}",
                        MediaType.APPLICATION_JSON
                ));

        TranscriptionResult result =
                client.transcribe(audio);

        assertThat(result.text())
                .isEqualTo("안녕하세요");
        assertThat(result.provider())
                .isEqualTo("OPENAI");
        assertThat(result.model())
                .isEqualTo(
                        "gpt-4o-mini-transcribe"
                );

        server.verify();
    }

    @Test
    void 잘못된_MIME_타입이면_400을_반환하고_OpenAI를_호출하지_않는다() {
        VoiceAudio invalidAudio = new VoiceAudio(
                "voice.m4a",
                "invalid-content-type",
                new byte[]{1, 2, 3}
        );

        assertError(
                () -> client.transcribe(invalidAudio),
                ErrorCode.COMPANION_AUDIO_FORMAT_UNSUPPORTED
        );

        server.verify();
    }

    @Test
    void 모델명이_없으면_호출하지_않고_503을_반환한다() {
        properties.setModel(" ");

        assertError(
                () -> client.transcribe(audio),
                ErrorCode.COMPANION_STT_NOT_CONFIGURED
        );

        server.verify();
    }

    @Test
    void API_KEY가_없으면_호출하지_않고_503을_반환한다() {
        properties.setApiKey("");

        assertError(
                () -> client.transcribe(audio),
                ErrorCode.COMPANION_STT_NOT_CONFIGURED
        );

        server.verify();
    }

    @Test
    void 인식_결과가_비어있으면_422를_반환한다() {
        server.expect(requestTo(
                        BASE_URL
                                + "/v1/audio/transcriptions"
                ))
                .andRespond(withSuccess(
                        "{\"text\":\"   \"}",
                        MediaType.APPLICATION_JSON
                ));

        assertError(
                () -> client.transcribe(audio),
                ErrorCode
                        .COMPANION_SPEECH_NOT_RECOGNIZED
        );

        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 429, 500})
    void OpenAI_오류를_502로_변환한다(
            int status
    ) {
        server.expect(requestTo(
                        BASE_URL
                                + "/v1/audio/transcriptions"
                ))
                .andRespond(withStatus(
                        HttpStatus.valueOf(status)
                ));

        assertError(
                () -> client.transcribe(audio),
                ErrorCode.COMPANION_STT_UNAVAILABLE
        );

        server.verify();
    }

    @Test
    void timeout을_504로_변환한다() {
        server.expect(requestTo(
                        BASE_URL
                                + "/v1/audio/transcriptions"
                ))
                .andRespond(request -> {
                    throw new SocketTimeoutException(
                            "timeout"
                    );
                });

        assertError(
                () -> client.transcribe(audio),
                ErrorCode.COMPANION_STT_TIMEOUT
        );

        server.verify();
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