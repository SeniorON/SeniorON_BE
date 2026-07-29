package com.example.senioron.domain.companion.service;

import com.example.senioron.domain.companion.entity.CompanionConversation;
import com.example.senioron.domain.companion.entity.CompanionTurn;
import com.example.senioron.domain.companion.repository.CompanionConversationRepository;
import com.example.senioron.domain.companion.repository.CompanionTurnRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompanionTurnCreationTransactionService {

    private final CompanionConversationRepository
            conversationRepository;

    private final CompanionTurnRepository
            turnRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long create(
            Long conversationId,
            String requestId
    ) {
        CompanionConversation conversation = conversationRepository
                .findByIdForUpdate(conversationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPANION_CONVERSATION_NOT_FOUND));

        if (!conversation.isActive()) {
            throw new BusinessException(ErrorCode.COMPANION_CONVERSATION_ENDED);
        }

        CompanionTurn turn = CompanionTurn.receive(
                conversation,
                requestId
        );

        CompanionTurn saved = turnRepository.saveAndFlush(turn);

        return saved.getTurnId();
    }
}