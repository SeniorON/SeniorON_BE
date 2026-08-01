package com.example.senioron.domain.companion.client.llm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AnthropicMessageResponse(
        String model,
        List<ContentBlock> content,

        @JsonProperty("stop_reason")
        String stopReason,

        Usage usage
) {
    public record ContentBlock(
            String type,
            String text
    ) {
    }

    public record Usage(
            @JsonProperty("input_tokens")
            Integer inputTokens,

            @JsonProperty("output_tokens")
            Integer outputTokens
    ) {
    }
}