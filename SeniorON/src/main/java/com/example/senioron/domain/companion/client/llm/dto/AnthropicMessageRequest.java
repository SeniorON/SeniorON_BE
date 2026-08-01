package com.example.senioron.domain.companion.client.llm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AnthropicMessageRequest(
        String model,

        @JsonProperty("max_tokens")
        int maxTokens,

        String system,

        List<Message> messages
) {
    public AnthropicMessageRequest {
        if (messages == null) {
            throw new IllegalArgumentException("Anthropic 메시지 목록은 필수입니다.");
        }

        messages = List.copyOf(messages);
    }

    public record Message(
            String role,
            String content
    ) {
    }
}