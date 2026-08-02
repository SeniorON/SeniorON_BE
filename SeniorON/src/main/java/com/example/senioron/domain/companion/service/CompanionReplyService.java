package com.example.senioron.domain.companion.service;

import com.example.senioron.domain.companion.client.llm.AnthropicConversationClient;
import com.example.senioron.domain.companion.config.CompanionPromptProvider;
import com.example.senioron.domain.companion.service.model.CompanionContextMessage;
import com.example.senioron.domain.companion.service.model.CompanionReplyResult;
import com.example.senioron.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CompanionReplyService {

    private final CompanionConversationService conversationService;

    private final CompanionPersistenceService persistenceService;

    private final AnthropicConversationClient anthropicClient;

    private final CompanionPromptProvider promptProvider;

    public CompanionReplyResult generate(
            User principal,
            Long conversationId
    ) {
        Long userId = conversationService.getOwnedActiveUserId(
                principal,
                conversationId
        );

        List<CompanionContextMessage> messages = persistenceService.loadRecentMessagesByUser(
                userId,
                promptProvider
                        .getRecentMessageLimit()
        );

        return anthropicClient.generateReply(
                promptProvider.getSystemPrompt(),
                promptProvider.getVersion(),
                messages
        );
    }
}