package com.example.senioron.domain.companion.client.safety;

import com.example.senioron.domain.companion.config.AnthropicProperties;
import com.example.senioron.domain.companion.config.CompanionSafetyPromptProvider;
import com.example.senioron.domain.companion.config.CompanionSafetyProperties;
import com.example.senioron.domain.companion.entity.MessageRole;
import com.example.senioron.domain.companion.entity.SafetyType;
import com.example.senioron.domain.companion.service.model.CompanionMessageContent;
import com.example.senioron.domain.companion.service.model.SafetyDecision;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

class AnthropicContextualSafetyClassifierTest {

    private static final String BASE_URL =
            "https://api.anthropic.com";

    private MockRestServiceServer server;

    private AnthropicContextualSafetyClassifier
            classifier;

    @BeforeEach
    void setUp() {
        AnthropicProperties
                anthropicProperties =
                new AnthropicProperties();

        anthropicProperties.setBaseUrl(
                BASE_URL
        );
        anthropicProperties.setApiKey(
                "test-api-key"
        );
        anthropicProperties.setVersion(
                "2023-06-01"
        );
        anthropicProperties.setModel(
                "claude-haiku-4-5-20251001"
        );

        CompanionSafetyProperties
                safetyProperties =
                new CompanionSafetyProperties();

        CompanionSafetyPromptProvider
                promptProvider =
                mock(
                        CompanionSafetyPromptProvider.class
                );

        given(
                promptProvider.getSystemPrompt()
        ).willReturn(
                "안전 분류 테스트 프롬프트"
        );

        RestClient.Builder builder =
                RestClient.builder()
                        .baseUrl(BASE_URL);

        server =
                MockRestServiceServer
                        .bindTo(builder)
                        .build();

        classifier =
                new AnthropicContextualSafetyClassifier(
                        builder.build(),
                        anthropicProperties,
                        safetyProperties,
                        promptProvider
                );
    }

    @Test
    void parsesNormalStructuredOutput() {
        server.expect(
                        requestTo(
                                BASE_URL
                                        + "/v1/messages"
                        )
                )
                .andExpect(
                        method(HttpMethod.POST)
                )
                .andRespond(
                        withSuccess(
                                """
                                {
                                  "model": "claude-haiku-4-5-20251001",
                                  "content": [
                                    {
                                      "type": "text",
                                      "text": "{\\"safetyType\\":\\"NORMAL\\"}"
                                    }
                                  ],
                                  "stop_reason": "end_turn",
                                  "usage": {
                                    "input_tokens": 30,
                                    "output_tokens": 8
                                  }
                                }
                                """,
                                MediaType.APPLICATION_JSON
                        )
                );

        SafetyDecision result =
                classifier.classify(
                        List.of(),
                        "오늘 점심 맛있게 먹었어"
                );

        assertThat(result.type())
                .isEqualTo(
                        SafetyType.NORMAL
                );

        assertThat(result.ruleId())
                .isNull();

        server.verify();
    }

    @Test
    void rejectsInvalidJsonResponse() {
        server.expect(
                        requestTo(
                                BASE_URL
                                        + "/v1/messages"
                        )
                )
                .andRespond(
                        withSuccess(
                                """
                                {
                                  "model": "claude-haiku-4-5-20251001",
                                  "content": [
                                    {
                                      "type": "text",
                                      "text": "not-json"
                                    }
                                  ],
                                  "stop_reason": "end_turn",
                                  "usage": {
                                    "input_tokens": 30,
                                    "output_tokens": 8
                                  }
                                }
                                """,
                                MediaType.APPLICATION_JSON
                        )
                );

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> classifier.classify(
                                List.of(),
                                "테스트 발화"
                        )
                );

        assertThat(exception.getErrorCode())
                .isEqualTo(
                        ErrorCode
                                .COMPANION_SAFETY_CLASSIFIER_INVALID_RESPONSE
                                .getCode()
                );

        server.verify();
    }
    @Test
    void rejectsMaxTokensResponse() {
        server.expect(
                        requestTo(
                                BASE_URL
                                        + "/v1/messages"
                        )
                )
                .andRespond(
                        withSuccess(
                                """
                                {
                                  "model": "claude-haiku-4-5-20251001",
                                  "content": [
                                    {
                                      "type": "text",
                                      "text": "{\\"safetyType\\":\\"EMERGENCY\\"}"
                                    }
                                  ],
                                  "stop_reason": "max_tokens",
                                  "usage": {
                                    "input_tokens": 30,
                                    "output_tokens": 64
                                  }
                                }
                                """,
                                MediaType.APPLICATION_JSON
                        )
                );

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> classifier.classify(
                                List.of(),
                                "테스트 발화"
                        )
                );

        assertThat(exception.getErrorCode())
                .isEqualTo(
                        ErrorCode
                                .COMPANION_SAFETY_CLASSIFIER_INVALID_RESPONSE
                                .getCode()
                );

        server.verify();
    }
    @Test
    void mapsServerErrorToUnavailable() {
        server.expect(
                        requestTo(
                                BASE_URL
                                        + "/v1/messages"
                        )
                )
                .andRespond(
                        withServerError()
                );

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> classifier.classify(
                                List.of(),
                                "테스트 발화"
                        )
                );

        assertThat(exception.getErrorCode())
                .isEqualTo(
                        ErrorCode
                                .COMPANION_SAFETY_CLASSIFIER_UNAVAILABLE
                                .getCode()
                );

        server.verify();
    }

    @Test
    void parsesEmergencyStructuredOutput() {
        server.expect(
                        requestTo(
                                BASE_URL
                                        + "/v1/messages"
                        )
                )
                .andExpect(
                        method(HttpMethod.POST)
                )
                .andExpect(
                        header(
                                "x-api-key",
                                "test-api-key"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.output_config.format.type"
                        ).value(
                                "json_schema"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.messages[0].content"
                        ).value(
                                org.hamcrest.Matchers
                                        .containsString(
                                                "죽고 싶어"
                                        )
                        )
                )
                .andRespond(
                        withSuccess(
                                """
                                {
                                  "model": "claude-haiku-4-5-20251001",
                                  "content": [
                                    {
                                      "type": "text",
                                      "text": "{\\"safetyType\\":\\"EMERGENCY\\"}"
                                    }
                                  ],
                                  "stop_reason": "end_turn",
                                  "usage": {
                                    "input_tokens": 40,
                                    "output_tokens": 10
                                  }
                                }
                                """,
                                MediaType.APPLICATION_JSON
                        )
                );

        SafetyDecision result =
                classifier.classify(
                        List.of(
                                new CompanionMessageContent(
                                        MessageRole.USER,
                                        "요즘 계속 힘들어"
                                )
                        ),
                        "죽고 싶어"
                );

        assertThat(result.isEmergency())
                .isTrue();

        assertThat(result.ruleId())
                .isEqualTo(
                        "CONTEXTUAL_CLASSIFIER_V1"
                );

        server.verify();
    }
}