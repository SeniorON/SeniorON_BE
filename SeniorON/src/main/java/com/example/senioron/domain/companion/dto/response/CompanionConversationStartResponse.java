package com.example.senioron.domain.companion.dto.response;

import com.example.senioron.domain.companion.entity.ConversationStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class CompanionConversationStartResponse {

    private final Long conversationId;
    private final ConversationStatus conversationStatus;
    private final LocalDateTime startedAt;
    private final boolean created;
}
