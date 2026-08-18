package com.example.senioron.domain.companion.client.llm;

import com.example.senioron.domain.companion.config.AnthropicProperties;
import com.example.senioron.domain.companion.entity.MessageRole;
import com.example.senioron.domain.companion.service.model.CompanionContextMessage;
import com.example.senioron.domain.companion.service.model.CompanionReplyResult;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AnthropicConversationClientTest {

    private static final String BASE_URL =
            "https://api.anthropic.com";

    private AnthropicProperties properties;

    private MockRestServiceServer server;

    private AnthropicConversationClient client;

    private List<CompanionContextMessage>
            messages;

    @BeforeEach
    void setUp() {
        properties =
                new AnthropicProperties();

        properties.setBaseUrl(BASE_URL);
        properties.setApiKey("test-api-key");
        properties.setVersion("2023-06-01");
        properties.setModel(
                "claude-haiku-4-5-20251001"
        );
        properties.setMaxTokens(256);

        RestClient.Builder builder =
                RestClient.builder()
                        .baseUrl(BASE_URL);

        server =
                MockRestServiceServer
                        .bindTo(builder)
                        .build();

        client =
                new AnthropicConversationClient(
                        builder.build(),
                        properties
                );

        messages =
                List.of(
                        new CompanionContextMessage(
                                MessageRole.USER,
                                "어제 공원에 갔어요.",
                                LocalDateTime.of(
                                        2026,
                                        7,
                                        31,
                                        18,
                                        30
                                )
                        ),
                        new CompanionContextMessage(
                                MessageRole.ASSISTANT,
                                "[발화 시각: 2026-07-31 18:31] 공원에 다녀오셨군요.",
                                LocalDateTime.of(
                                        2026,
                                        7,
                                        31,
                                        18,
                                        31
                                )
                        ),
                        new CompanionContextMessage(
                                MessageRole.USER,
                                "오늘도 가볼까요?",
                                LocalDateTime.of(
                                        2026,
                                        8,
                                        1,
                                        9,
                                        10
                                )
                        )
                );
    }

    @Test
    void 대화_기록을_전송하고_답변과_메타데이터를_반환한다() {
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
                        header(
                                "anthropic-version",
                                "2023-06-01"
                        )
                )
                .andExpect(
                        content()
                                .contentTypeCompatibleWith(
                                        MediaType
                                                .APPLICATION_JSON
                                )
                )
                .andExpect(
                        jsonPath("$.model")
                                .value(
                                        "claude-haiku-4-5-20251001"
                                )
                )
                .andExpect(
                        jsonPath("$.max_tokens")
                                .value(256)
                )
                .andExpect(
                        jsonPath("$.system")
                                .value(
                                        "테스트 시스템 프롬프트"
                                )
                )
                .andExpect(
                        jsonPath(
                                "$.messages[0].role"
                        ).value("user")
                )
                .andExpect(
                        jsonPath(
                                "$.messages[0].content"
                        ).value(
                                "어제 공원에 갔어요."
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.messages[1].role"
                        ).value("assistant")
                )
                .andExpect(
                        jsonPath(
                                "$.messages[1].content"
                        ).value(
                                "공원에 다녀오셨군요."
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.messages[2].role"
                        ).value("user")
                )
                .andExpect(
                        jsonPath(
                                "$.messages[2].content"
                        ).value(
                                "오늘도 가볼까요?"
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
                                      "text": " 오늘도 공원에 가시면 기분이 좋으시겠어요. "
                                    }
                                  ],
                                  "stop_reason": "end_turn",
                                  "usage": {
                                    "input_tokens": 120,
                                    "output_tokens": 25
                                  }
                                }
                                """,
                                MediaType
                                        .APPLICATION_JSON
                        )
                );

        CompanionReplyResult result =
                client.generateReply(
                        "테스트 시스템 프롬프트",
                        "v1",
                        messages
                );

        assertThat(result.text())
                .isEqualTo(
                        "오늘도 공원에 가시면 기분이 좋으시겠어요."
                );

        assertThat(result.provider())
                .isEqualTo("ANTHROPIC");

        assertThat(result.model())
                .isEqualTo(
                        "claude-haiku-4-5-20251001"
                );

        assertThat(result.promptVersion())
                .isEqualTo("v1");

        assertThat(result.inputTokens())
                .isEqualTo(120);

        assertThat(result.outputTokens())
                .isEqualTo(25);

        server.verify();
    }

    @Test
    void 응답_앞의_발화_시각을_제거한다() {
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
                                      "text": "[발화 시각: 2026-08-18 23:09] 특별히 정해진 주제는 없습니다."
                                    }
                                  ],
                                  "stop_reason": "end_turn",
                                  "usage": {
                                    "input_tokens": 50,
                                    "output_tokens": 15
                                  }
                                }
                                """,
                                MediaType.APPLICATION_JSON
                        )
                );

        CompanionReplyResult result =
                client.generateReply(
                        "테스트 시스템 프롬프트",
                        "v2",
                        messages
                );

        assertThat(result.text())
                .isEqualTo(
                        "특별히 정해진 주제는 없습니다."
                );

        assertThat(result.promptVersion())
                .isEqualTo("v2");

        server.verify();
    }

    @Test
    void API_KEY가_없으면_503을_반환하고_호출하지_않는다() {
        properties.setApiKey("");

        assertError(
                () -> client.generateReply(
                        "시스템 프롬프트",
                        "v1",
                        messages
                ),
                ErrorCode
                        .COMPANION_LLM_NOT_CONFIGURED
        );

        server.verify();
    }

    @Test
    void 모델명이_없으면_503을_반환하고_호출하지_않는다() {
        properties.setModel(" ");

        assertError(
                () -> client.generateReply(
                        "시스템 프롬프트",
                        "v1",
                        messages
                ),
                ErrorCode
                        .COMPANION_LLM_NOT_CONFIGURED
        );

        server.verify();
    }

    @Test
    void 대화_메시지가_없으면_400을_반환하고_호출하지_않는다() {
        assertError(
                () -> client.generateReply(
                        "시스템 프롬프트",
                        "v1",
                        List.of()
                ),
                ErrorCode.BAD_REQUEST
        );

        server.verify();
    }

    @Test
    void 응답_text가_공백이면_빈_응답_오류가_발생한다() {
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
                                      "text": "   "
                                    }
                                  ],
                                  "stop_reason": "end_turn",
                                  "usage": {
                                    "input_tokens": 120,
                                    "output_tokens": 1
                                  }
                                }
                                """,
                                MediaType
                                        .APPLICATION_JSON
                        )
                );

        assertError(
                () -> generate(),
                ErrorCode
                        .COMPANION_LLM_EMPTY_RESPONSE
        );

        server.verify();
    }

    @Test
    void text_블록이_없으면_빈_응답_오류가_발생한다() {
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
                                      "type": "tool_use"
                                    }
                                  ],
                                  "stop_reason": "end_turn",
                                  "usage": {
                                    "input_tokens": 120,
                                    "output_tokens": 1
                                  }
                                }
                                """,
                                MediaType
                                        .APPLICATION_JSON
                        )
                );

        assertError(
                () -> generate(),
                ErrorCode
                        .COMPANION_LLM_EMPTY_RESPONSE
        );

        server.verify();
    }

    @Test
    void 정상_종료가_아니면_제공자_오류가_발생한다() {
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
                                      "text": "답변이 중간에 잘렸습니다."
                                    }
                                  ],
                                  "stop_reason": "max_tokens",
                                  "usage": {
                                    "input_tokens": 120,
                                    "output_tokens": 256
                                  }
                                }
                                """,
                                MediaType
                                        .APPLICATION_JSON
                        )
                );

        assertError(
                () -> generate(),
                ErrorCode
                        .COMPANION_LLM_UNAVAILABLE
        );

        server.verify();
    }

    @Test
    void usage가_없으면_제공자_오류가_발생한다() {
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
                                      "text": "정상처럼 보이는 답변입니다."
                                    }
                                  ],
                                  "stop_reason": "end_turn"
                                }
                                """,
                                MediaType
                                        .APPLICATION_JSON
                        )
                );

        assertError(
                () -> generate(),
                ErrorCode
                        .COMPANION_LLM_UNAVAILABLE
        );

        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 429, 500})
    void Anthropic_오류를_502로_변환한다(
            int status
    ) {
        server.expect(
                        requestTo(
                                BASE_URL
                                        + "/v1/messages"
                        )
                )
                .andRespond(
                        withStatus(
                                HttpStatus.valueOf(
                                        status
                                )
                        )
                );

        assertError(
                () -> generate(),
                ErrorCode
                        .COMPANION_LLM_UNAVAILABLE
        );

        server.verify();
    }

    @Test
    void timeout을_504로_변환한다() {
        server.expect(
                        requestTo(
                                BASE_URL
                                        + "/v1/messages"
                        )
                )
                .andRespond(request -> {
                    throw new SocketTimeoutException(
                            "timeout"
                    );
                });

        assertError(
                () -> generate(),
                ErrorCode
                        .COMPANION_LLM_TIMEOUT
        );

        server.verify();
    }

    private CompanionReplyResult generate() {
        return client.generateReply(
                "테스트 시스템 프롬프트",
                "v1",
                messages
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

    @Test
    void 선행_ASSISTANT_메시지를_제외하고_요청한다() {
        messages =
                List.of(
                        new CompanionContextMessage(
                                MessageRole.ASSISTANT,
                                "이전 답변",
                                LocalDateTime.of(
                                        2026,
                                        7,
                                        31,
                                        18,
                                        30
                                )
                        ),
                        new CompanionContextMessage(
                                MessageRole.USER,
                                "오늘도 산책할까요?",
                                LocalDateTime.of(
                                        2026,
                                        8,
                                        1,
                                        9,
                                        10
                                )
                        )
                );

        server.expect(
                        requestTo(
                                BASE_URL
                                        + "/v1/messages"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.messages.length()"
                        ).value(1)
                )
                .andExpect(
                        jsonPath(
                                "$.messages[0].role"
                        ).value("user")
                )
                .andExpect(
                        jsonPath(
                                "$.messages[0].content"
                        ).value(
                                "오늘도 산책할까요?"
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
                                      "text": "날씨가 좋으면 가볍게 다녀오세요."
                                    }
                                  ],
                                  "stop_reason": "end_turn",
                                  "usage": {
                                    "input_tokens": 50,
                                    "output_tokens": 12
                                  }
                                }
                                """,
                                MediaType.APPLICATION_JSON
                        )
                );

        CompanionReplyResult result =
                client.generateReply(
                        "테스트 시스템 프롬프트",
                        "v1",
                        messages
                );

        assertThat(result.text())
                .isEqualTo(
                        "날씨가 좋으면 가볍게 다녀오세요."
                );

        server.verify();
    }
}
