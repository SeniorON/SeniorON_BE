package com.example.senioron.domain.companion.client.safety.dto;

import com.example.senioron.domain.companion.entity.SafetyType;

public record AnthropicSafetyPayload(
        SafetyType safetyType
) {
}