package com.example.senioron.domain.companion.client.tts;

import com.example.senioron.domain.companion.config.GoogleTtsProperties;
import com.example.senioron.domain.companion.service.model.SynthesizedAudio;
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
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class GoogleSpeechSynthesisClientTest {

    @Mock
    private ObjectProvider<TextToSpeechClient>
            clientProvider;

    @Mock
    private TextToSpeechClient googleClient;

    private GoogleTtsProperties properties;
    private GoogleSpeechSynthesisClient client;

    @BeforeEach
    void setUp() {
        properties = new GoogleTtsProperties();

        client = new GoogleSpeechSynthesisClient(
                clientProvider,
                properties
        );
    }

    @Test
    void 설정값으로_MP3_음성을_합성한다() {
        when(clientProvider.getObject())
                .thenReturn(googleClient);

        SynthesizeSpeechResponse response =
                SynthesizeSpeechResponse.newBuilder()
                        .setAudioContent(
                                ByteString.copyFrom(
                                        new byte[]{1, 2, 3}
                                )
                        )
                        .build();

        when(googleClient.synthesizeSpeech(
                any(SynthesisInput.class),
                any(VoiceSelectionParams.class),
                any(AudioConfig.class)
        )).thenReturn(response);

        SynthesizedAudio result =
                client.synthesize(
                        "안녕하세요. 오늘 기분은 어떠세요?"
                );

        assertThat(result.bytes())
                .containsExactly(1, 2, 3);
        assertThat(result.contentType())
                .isEqualTo("audio/mpeg");
        assertThat(result.format())
                .isEqualTo("mp3");
        assertThat(result.provider())
                .isEqualTo("GOOGLE_CLOUD");
        assertThat(result.voice())
                .isEqualTo("ko-KR-Neural2-A");

        ArgumentCaptor<SynthesisInput> inputCaptor =
                ArgumentCaptor.forClass(
                        SynthesisInput.class
                );

        ArgumentCaptor<VoiceSelectionParams>
                voiceCaptor =
                ArgumentCaptor.forClass(
                        VoiceSelectionParams.class
                );

        ArgumentCaptor<AudioConfig> configCaptor =
                ArgumentCaptor.forClass(
                        AudioConfig.class
                );

        verify(googleClient).synthesizeSpeech(
                inputCaptor.capture(),
                voiceCaptor.capture(),
                configCaptor.capture()
        );

        assertThat(inputCaptor.getValue().getText())
                .isEqualTo(
                        "안녕하세요. 오늘 기분은 어떠세요?"
                );

        assertThat(
                voiceCaptor.getValue()
                        .getLanguageCode()
        ).isEqualTo("ko-KR");

        assertThat(
                voiceCaptor.getValue().getName()
        ).isEqualTo("ko-KR-Neural2-A");

        assertThat(
                configCaptor.getValue()
                        .getAudioEncoding()
        ).isEqualTo(AudioEncoding.MP3);

        assertThat(
                configCaptor.getValue()
                        .getSpeakingRate()
        ).isEqualTo(0.9);

        assertThat(
                configCaptor.getValue().getPitch()
        ).isEqualTo(0.0);

        assertThat(
                configCaptor.getValue()
                        .getVolumeGainDb()
        ).isEqualTo(0.0);
    }

    @Test
    void 빈_문장은_외부_API를_호출하지_않고_거부한다() {
        assertThrows(
                IllegalArgumentException.class,
                () -> client.synthesize(" ")
        );
    }

    @Test
    void UTF8_5000바이트를_초과하면_거부한다() {
        // 한글 한 글자는 UTF-8에서 보통 3바이트
        String overLimitText =
                "가".repeat(1_667);

        assertThrows(
                IllegalArgumentException.class,
                () -> client.synthesize(
                        overLimitText
                )
        );
    }

    @Test
    void Google이_빈_음성을_반환하면_502로_변환한다() {
        when(clientProvider.getObject())
                .thenReturn(googleClient);

        when(googleClient.synthesizeSpeech(
                any(SynthesisInput.class),
                any(VoiceSelectionParams.class),
                any(AudioConfig.class)
        )).thenReturn(
                SynthesizeSpeechResponse
                        .getDefaultInstance()
        );

        assertBusinessError(
                ErrorCode.COMPANION_TTS_UNAVAILABLE
        );
    }

    @Test
    void ADC_생성에_실패하면_503으로_변환한다() {
        BeanCreationException creationException =
                new BeanCreationException(
                        "googleTextToSpeechClient",
                        "Google TTS client creation failed",
                        new IOException(
                                "ADC not found"
                        )
                );

        when(clientProvider.getObject())
                .thenThrow(creationException);

        assertBusinessError(
                ErrorCode
                        .COMPANION_TTS_NOT_CONFIGURED
        );
    }

    @ParameterizedTest
    @EnumSource(
            value = StatusCode.Code.class,
            names = {
                    "UNAUTHENTICATED",
                    "PERMISSION_DENIED",
                    "INVALID_ARGUMENT",
                    "FAILED_PRECONDITION",
                    "OUT_OF_RANGE"
            }
    )
    void 인증_권한_설정_오류는_503으로_변환한다(
            StatusCode.Code code
    ) {
        stubApiFailure(code);

        assertBusinessError(
                ErrorCode
                        .COMPANION_TTS_NOT_CONFIGURED
        );
    }

    @Test
    void deadline_초과는_504로_변환한다() {
        stubApiFailure(
                StatusCode.Code.DEADLINE_EXCEEDED
        );

        assertBusinessError(
                ErrorCode.COMPANION_TTS_TIMEOUT
        );
    }

    @ParameterizedTest
    @EnumSource(
            value = StatusCode.Code.class,
            names = {
                    "RESOURCE_EXHAUSTED",
                    "UNAVAILABLE",
                    "INTERNAL",
                    "UNKNOWN"
            }
    )
    void Google_서비스_오류는_502로_변환한다(
            StatusCode.Code code
    ) {
        stubApiFailure(code);

        assertBusinessError(
                ErrorCode.COMPANION_TTS_UNAVAILABLE
        );
    }

    @Test
    void MP3가_아닌_설정은_503으로_거부한다() {
        properties.setAudioEncoding("LINEAR16");

        assertBusinessError(
                ErrorCode
                        .COMPANION_TTS_NOT_CONFIGURED
        );
    }

    @Test
    void 발화_속도가_2점을_초과하면_API_호출_전에_거부한다() {
        properties.setSpeakingRate(2.1);

        assertBusinessError(
                ErrorCode.COMPANION_TTS_NOT_CONFIGURED
        );

        verifyNoInteractions(
                clientProvider,
                googleClient
        );
    }

    private void stubApiFailure(
            StatusCode.Code code
    ) {
        when(clientProvider.getObject())
                .thenReturn(googleClient);

        ApiException apiException =
                mock(ApiException.class);

        StatusCode statusCode =
                mock(StatusCode.class);

        when(apiException.getStatusCode())
                .thenReturn(statusCode);

        when(statusCode.getCode())
                .thenReturn(code);

        when(googleClient.synthesizeSpeech(
                any(SynthesisInput.class),
                any(VoiceSelectionParams.class),
                any(AudioConfig.class)
        )).thenThrow(apiException);
    }

    private void assertBusinessError(
            ErrorCode expectedErrorCode
    ) {
        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> client.synthesize(
                                "안녕하세요."
                        )
                );

        assertThat(exception.getCode())
                .isEqualTo(expectedErrorCode);
    }
}