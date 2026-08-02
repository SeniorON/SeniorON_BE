package com.example.senioron.domain.companion.service.model;

import com.example.senioron.domain.companion.entity.MessageRole;

import java.time.LocalDateTime;

public record CompanionContextMessage(
        MessageRole role,
        String content,
        LocalDateTime occurredAt
) {
    public CompanionContextMessage {
        if (role == null) {
            throw new IllegalArgumentException("메시지 역할은 필수입니다.");
        }

        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("메시지 내용은 필수입니다.");
        }

        content = content.trim();
    }
}