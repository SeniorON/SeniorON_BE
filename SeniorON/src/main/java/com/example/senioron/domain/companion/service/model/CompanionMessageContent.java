package com.example.senioron.domain.companion.service.model;

import com.example.senioron.domain.companion.entity.MessageRole;

public record CompanionMessageContent(
        MessageRole role,
        String content
) {
}
