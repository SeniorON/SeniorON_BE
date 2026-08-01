package com.example.senioron.domain.companion.client.llm;

import com.example.senioron.domain.companion.client.llm.dto.AnthropicMessageRequest;
import com.example.senioron.domain.companion.client.llm.dto.AnthropicMessageResponse;
import com.example.senioron.domain.companion.config.AnthropicProperties;
import com.example.senioron.domain.companion.service.model.CompanionContextMessage;
import com.example.senioron.domain.companion.service.model.CompanionReplyResult;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@Slf4j
public class AnthropicConversationClient {

    private static final String PROVIDER = "ANTHROPIC";

    private static final DateTimeFormatter
            MESSAGE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final RestClient restClient;

    private final AnthropicProperties
            properties;

    public AnthropicConversationClient(
            @Qualifier("anthropicRestClient")
            RestClient restClient,
            AnthropicProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public CompanionReplyResult generateReply(
            String systemPrompt,
            String promptVersion,
            List<CompanionContextMessage> messages
    ) {
        validateConfiguration();

        validatePrompt(
                systemPrompt,
                promptVersion,
                messages
        );

        AnthropicMessageRequest request = createRequest(
                systemPrompt,
                messages
        );

        AnthropicMessageResponse response = requestMessage(request);

        return createResult(
                response,
                promptVersion
        );
    }

    private void validateConfiguration() {
        if (properties.getApiKey() == null
                || properties
                .getApiKey()
                .isBlank()
                || properties.getModel() == null
                || properties
                .getModel()
                .isBlank()
                || properties.getVersion() == null
                || properties
                .getVersion()
                .isBlank()
                || properties.getMaxTokens() <= 0) {

            throw new BusinessException(ErrorCode.COMPANION_LLM_NOT_CONFIGURED);
        }
    }

    private void validatePrompt(
            String systemPrompt,
            String promptVersion,
            List<CompanionContextMessage> messages
    ) {
        if (systemPrompt == null
                || systemPrompt.isBlank()
                || promptVersion == null
                || promptVersion.isBlank()
                || messages == null
                || messages.isEmpty()) {

            throw new BusinessException(
                    ErrorCode.BAD_REQUEST
            );
        }
    }

    private AnthropicMessageRequest
    createRequest(
            String systemPrompt,
            List<CompanionContextMessage> messages
    ) {
        List<AnthropicMessageRequest.Message>
                requestMessages =
                messages.stream()
                        .map(this::toRequestMessage)
                        .toList();

        return new AnthropicMessageRequest(
                properties.getModel(),
                properties.getMaxTokens(),
                systemPrompt,
                requestMessages
        );
    }

    private AnthropicMessageRequest.Message
    toRequestMessage(
            CompanionContextMessage message
    ) {
        String role =
                switch (message.role()) {
                    case USER -> "user";
                    case ASSISTANT -> "assistant";
                };

        String content =
                message.content();

        if (message.occurredAt() != null) {
            content =
                    "[발화 시각: "
                            + message.occurredAt()
                            .format(
                                    MESSAGE_TIME_FORMATTER
                            )
                            + "] "
                            + content;
        }

        return new AnthropicMessageRequest.Message(
                role,
                content
        );
    }

    private AnthropicMessageResponse
    requestMessage(
            AnthropicMessageRequest request
    ) {
        try {
            return restClient.post()
                    .uri("/v1/messages")
                    .header(
                            "x-api-key",
                            properties.getApiKey()
                    )
                    .header(
                            "anthropic-version",
                            properties.getVersion()
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(
                            AnthropicMessageResponse
                                    .class
                    );

        } catch (
                RestClientResponseException exception
        ) {
            logProviderFailure(exception);

            throw new BusinessException(ErrorCode.COMPANION_LLM_UNAVAILABLE);

        } catch (
                ResourceAccessException exception
        ) {
            if (hasTimeoutCause(exception)) {
                throw new BusinessException(ErrorCode.COMPANION_LLM_TIMEOUT);
            }

            throw new BusinessException(ErrorCode.COMPANION_LLM_UNAVAILABLE);

        } catch (
                RestClientException exception
        ) {
            throw new BusinessException(ErrorCode.COMPANION_LLM_UNAVAILABLE);
        }
    }

    private CompanionReplyResult createResult(
            AnthropicMessageResponse response,
            String promptVersion
    ) {
        if (response == null) {
            throw unavailableException();
        }

        if (!"end_turn".equals(
                response.stopReason()
        )) {
            throw unavailableException();
        }

        String text = findFirstText(response);

        if (text == null || text.isBlank()) {
            throw new BusinessException(ErrorCode.COMPANION_LLM_EMPTY_RESPONSE);
        }

        if (response.model() == null
                || response.model().isBlank()
                || response.usage() == null
                || response.usage()
                .inputTokens() == null
                || response.usage()
                .outputTokens() == null) {

            throw unavailableException();
        }

        return new CompanionReplyResult(
                text,
                PROVIDER,
                response.model(),
                promptVersion,
                response.usage().inputTokens(),
                response.usage().outputTokens()
        );
    }

    private String findFirstText(
            AnthropicMessageResponse response
    ) {
        if (response.content() == null) {
            return null;
        }

        return response.content().stream()
                .filter(block ->
                        "text".equals(
                                block.type()
                        )
                )
                .map(
                        AnthropicMessageResponse
                                .ContentBlock::text
                )
                .filter(text ->
                        text != null
                                && !text.isBlank()
                )
                .findFirst()
                .orElse(null);
    }

    private void logProviderFailure(
            RestClientResponseException exception
    ) {
        String requestId =
                exception.getResponseHeaders()
                        == null
                        ? null
                        : exception
                        .getResponseHeaders()
                        .getFirst(
                                "request-id"
                        );

        log.warn(
                "Anthropic LLM request failed: status={}, requestId={}",
                exception.getStatusCode(),
                requestId
        );
    }

    private boolean hasTimeoutCause(
            Throwable throwable
    ) {
        Throwable cause = throwable;

        while (cause != null) {
            if (cause
                    instanceof java.net
                    .SocketTimeoutException
                    || cause
                    instanceof java.net.http
                    .HttpTimeoutException) {

                return true;
            }

            cause = cause.getCause();
        }

        return false;
    }

    private BusinessException
    unavailableException() {
        return new BusinessException(ErrorCode.COMPANION_LLM_UNAVAILABLE);
    }
}