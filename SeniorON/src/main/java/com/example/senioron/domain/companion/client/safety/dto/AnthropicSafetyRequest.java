package com.example.senioron.domain.companion.client.safety.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record AnthropicSafetyRequest(
        String model,

        @JsonProperty("max_tokens")
        int maxTokens,

        String system,

        List<Message> messages,

        @JsonProperty("output_config")
        OutputConfig outputConfig
) {

    public static OutputConfig safetyOutputConfig() {
        Map<String, Object> schema =
                Map.of(
                        "type",
                        "object",

                        "properties",
                        Map.of(
                                "safetyType",
                                Map.of(
                                        "type",
                                        "string",
                                        "enum",
                                        List.of(
                                                "NORMAL",
                                                "EMERGENCY"
                                        )
                                )
                        ),

                        "required",
                        List.of("safetyType"),

                        "additionalProperties",
                        false
                );

        return new OutputConfig(
                new Format(
                        "json_schema",
                        schema
                )
        );
    }

    public record Message(
            String role,
            String content
    ) {
    }

    public record OutputConfig(
            Format format
    ) {
    }

    public record Format(
            String type,
            Map<String, Object> schema
    ) {
    }
}