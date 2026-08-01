package com.example.senioron.domain.companion.service.model;

public record CompanionReplyResult(
        String text,
        String provider,
        String model,
        String promptVersion,
        Integer inputTokens,
        Integer outputTokens
) {
    public CompanionReplyResult {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("말벗 답변은 비어 있을 수 없습니다.");
        }

        text = text.trim();
    }
}