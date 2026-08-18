package com.example.senioron.domain.companion.client.llm;

import com.example.senioron.domain.companion.client.llm.dto.AnthropicMessageRequest;
import com.example.senioron.domain.companion.client.llm.dto.AnthropicMessageResponse;
import com.example.senioron.domain.companion.config.AnthropicProperties;
import com.example.senioron.domain.companion.entity.MessageRole;
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

import java.util.List;
import java.util.regex.Pattern;

@Component
@Slf4j
public class AnthropicConversationClient {

    private static final String PROVIDER = "ANTHROPIC";

    private static final Pattern
            LEADING_SPEECH_TIME_PATTERN =
            Pattern.compile(
                    "^\\s*\\[발화\\s*시각\\s*:\\s*"
                            + "\\d{4}-\\d{1,2}-\\d{1,2}\\s+"
                            + "\\d{1,2}:\\d{2}\\]\\s*"
            );

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

    private AnthropicMessageRequest createRequest(
            String systemPrompt,
            List<CompanionContextMessage> messages
    ) {
        List<CompanionContextMessage> normalizedMessages =
                removeLeadingAssistantMessages(
                        messages
                );

        List<AnthropicMessageRequest.Message>
                requestMessages =
                normalizedMessages.stream()
                        .map(this::toRequestMessage)
                        .toList();

        return new AnthropicMessageRequest(
                properties.getModel(),
                properties.getMaxTokens(),
                systemPrompt,
                requestMessages
        );
    }

    private List<CompanionContextMessage>
    removeLeadingAssistantMessages(
            List<CompanionContextMessage> messages
    ) {
        List<CompanionContextMessage> normalized =
                messages.stream()
                        .dropWhile(message ->
                                message.role()
                                        == MessageRole.ASSISTANT
                        )
                        .toList();

        if (normalized.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST
            );
        }

        return normalized;
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
                message.role() == MessageRole.ASSISTANT
                        ? sanitizeResponseText(
                        message.content()
                )
                        : message.content();

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

            throw new BusinessException(
                    ErrorCode.COMPANION_LLM_UNAVAILABLE
            );

        } catch (
                ResourceAccessException exception
        ) {
            if (hasTimeoutCause(exception)) {
                log.warn(
                        "Anthropic LLM request timed out",
                        exception
                );

                throw new BusinessException(
                        ErrorCode.COMPANION_LLM_TIMEOUT
                );
            }

            log.warn(
                    "Anthropic LLM resource access failed",
                    exception
            );

            throw new BusinessException(
                    ErrorCode.COMPANION_LLM_UNAVAILABLE
            );

        } catch (
                RestClientException exception
        ) {
            log.warn(
                    "Anthropic LLM request failed",
                    exception
            );

            throw new BusinessException(
                    ErrorCode.COMPANION_LLM_UNAVAILABLE
            );
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

        String text =
                sanitizeResponseText(
                        findFirstText(response)
                );

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

    private String sanitizeResponseText(
            String text
    ) {
        if (text == null) {
            return null;
        }

        return LEADING_SPEECH_TIME_PATTERN
                .matcher(text)
                .replaceFirst("")
                .strip();
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
