package com.example.senioron.domain.companion.client.safety;

import com.example.senioron.domain.companion.client.llm.dto.AnthropicMessageResponse;
import com.example.senioron.domain.companion.client.safety.dto.AnthropicSafetyPayload;
import com.example.senioron.domain.companion.client.safety.dto.AnthropicSafetyRequest;
import com.example.senioron.domain.companion.config.AnthropicProperties;
import com.example.senioron.domain.companion.config.CompanionSafetyPromptProvider;
import com.example.senioron.domain.companion.config.CompanionSafetyProperties;
import com.example.senioron.domain.companion.entity.SafetyType;
import com.example.senioron.domain.companion.service.model.CompanionMessageContent;
import com.example.senioron.domain.companion.service.model.SafetyDecision;
import com.example.senioron.domain.companion.service.port.ContextualSafetyClassifierPort;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

@Component
@Slf4j
public class AnthropicContextualSafetyClassifier
        implements ContextualSafetyClassifierPort {

    private static final String CLASSIFIER_RULE_ID = "CONTEXTUAL_CLASSIFIER_V1";

    private final RestClient restClient;
    private final AnthropicProperties anthropicProperties;
    private final CompanionSafetyProperties safetyProperties;
    private final CompanionSafetyPromptProvider promptProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AnthropicContextualSafetyClassifier(
            @Qualifier("anthropicRestClient")
            RestClient restClient,
            AnthropicProperties anthropicProperties,
            CompanionSafetyProperties safetyProperties,
            CompanionSafetyPromptProvider promptProvider
    ) {
        this.restClient = restClient;
        this.anthropicProperties = anthropicProperties;
        this.safetyProperties = safetyProperties;
        this.promptProvider = promptProvider;
    }

    @Override
    public SafetyDecision classify(
            List<CompanionMessageContent> recentMessages,
            String currentUtterance
    ) {
        validateConfiguration();
        validateUtterance(currentUtterance);

        AnthropicSafetyRequest request =
                new AnthropicSafetyRequest(
                        anthropicProperties.getModel(),
                        safetyProperties.getMaxTokens(),
                        promptProvider.getSystemPrompt(),
                        List.of(
                                new AnthropicSafetyRequest.Message(
                                        "user",
                                        createClassifierInput(
                                                recentMessages,
                                                currentUtterance
                                        )
                                )
                        ),
                        AnthropicSafetyRequest
                                .safetyOutputConfig()
                );

        AnthropicMessageResponse response =
                requestMessage(request);

        return parseDecision(response);
    }

    private void validateConfiguration() {
        if (anthropicProperties.getApiKey() == null
                || anthropicProperties
                .getApiKey()
                .isBlank()
                || anthropicProperties
                .getModel() == null
                || anthropicProperties
                .getModel()
                .isBlank()) {

            throw new BusinessException(ErrorCode.COMPANION_SAFETY_CLASSIFIER_NOT_CONFIGURED);
        }
    }

    private void validateUtterance(
            String currentUtterance
    ) {
        if (currentUtterance == null || currentUtterance.isBlank()) {

            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
    }

    private String createClassifierInput(
            List<CompanionMessageContent> recentMessages,
            String currentUtterance
    ) {
        StringBuilder input =
                new StringBuilder();

        input.append("[최근 대화]\n");

        if (recentMessages == null
                || recentMessages.isEmpty()) {

            input.append("없음\n");
        } else {
            recentMessages.forEach(message ->
                    input.append(
                                    message.role().name()
                            )
                            .append(": ")
                            .append(
                                    message.content()
                            )
                            .append("\n")
            );
        }

        input.append("\n[현재 발화]\n")
                .append(currentUtterance);

        return input.toString();
    }

    private AnthropicMessageResponse requestMessage(
            AnthropicSafetyRequest request
    ) {
        try {
            return restClient.post()
                    .uri("/v1/messages")
                    .header(
                            "x-api-key",
                            anthropicProperties
                                    .getApiKey()
                    )
                    .header(
                            "anthropic-version",
                            anthropicProperties
                                    .getVersion()
                    )
                    .contentType(
                            MediaType.APPLICATION_JSON
                    )
                    .accept(
                            MediaType.APPLICATION_JSON
                    )
                    .body(request)
                    .retrieve()
                    .body(
                            AnthropicMessageResponse.class
                    );

        } catch (RestClientResponseException exception) {
            log.warn(
                    "Anthropic 안전 분류 요청 실패: status={}",
                    exception.getStatusCode()
            );

            throw new BusinessException(ErrorCode.COMPANION_SAFETY_CLASSIFIER_UNAVAILABLE);
        } catch (ResourceAccessException exception) {
            if (hasTimeoutCause(exception)) {
                throw new BusinessException(ErrorCode.COMPANION_SAFETY_CLASSIFIER_TIMEOUT);
            }

            throw new BusinessException(ErrorCode.COMPANION_SAFETY_CLASSIFIER_UNAVAILABLE);
        } catch (RestClientException exception) {
            throw new BusinessException(ErrorCode.COMPANION_SAFETY_CLASSIFIER_UNAVAILABLE);
        }
    }

    private SafetyDecision parseDecision(
            AnthropicMessageResponse response
    ) {
        if (response == null
                || !"end_turn".equals(
                response.stopReason()
        )
                || response.content() == null) {

            throw invalidResponseException();
        }

        String json =
                response.content()
                        .stream()
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
                        .orElseThrow(
                                this::invalidResponseException
                        );

        try {
            AnthropicSafetyPayload payload =
                    objectMapper.readValue(
                            json,
                            AnthropicSafetyPayload.class
                    );

            if (payload.safetyType() == SafetyType.EMERGENCY) {

                return SafetyDecision.emergency(CLASSIFIER_RULE_ID);
            }

            if (payload.safetyType() == SafetyType.NORMAL) {

                return SafetyDecision.normal();
            }

            throw invalidResponseException();

        } catch (JsonProcessingException exception) {
            throw invalidResponseException();
        }
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
    invalidResponseException() {
        return new BusinessException(ErrorCode.COMPANION_SAFETY_CLASSIFIER_INVALID_RESPONSE);
    }
}