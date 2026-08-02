package com.example.senioron.domain.companion.service;

import com.example.senioron.domain.companion.crypto.CompanionTextCipher;
import com.example.senioron.domain.companion.entity.CompanionMessage;
import com.example.senioron.domain.companion.entity.CompanionTurn;
import com.example.senioron.domain.companion.entity.FailureStage;
import com.example.senioron.domain.companion.entity.MessageRole;
import com.example.senioron.domain.companion.entity.SafetyType;
import com.example.senioron.domain.companion.repository.CompanionConversationRepository;
import com.example.senioron.domain.companion.repository.CompanionMessageRepository;
import com.example.senioron.domain.companion.repository.CompanionTurnRepository;
import com.example.senioron.domain.companion.service.model.CompanionContextMessage;
import com.example.senioron.domain.companion.service.model.CompanionMessageContent;
import com.example.senioron.domain.companion.service.model.TurnCreationResult;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompanionPersistenceService {

    private static final int MAX_RECENT_MESSAGE_LIMIT = 20;

    private final CompanionConversationRepository
            conversationRepository;

    private final CompanionTurnRepository
            turnRepository;

    private final CompanionMessageRepository
            messageRepository;

    private final CompanionTurnCreationTransactionService
            turnCreationTransactionService;

    private final CompanionTextCipher textCipher;

    @Transactional(propagation = Propagation.NEVER)
    public TurnCreationResult createTurn(
            Long conversationId,
            String requestId
    ) {
        Optional<CompanionTurn> existing = turnRepository.findByConversationConversationIdAndRequestId(
                conversationId,
                requestId
        );

        if (existing.isPresent()) {
            return TurnCreationResult.alreadyExists();
        }

        try {
            Long createdTurnId = turnCreationTransactionService.create(
                    conversationId,
                    requestId
            );

            return TurnCreationResult.created(createdTurnId);
        } catch (
                DataIntegrityViolationException exception
        ) {
            boolean duplicateExists = turnRepository.findByConversationConversationIdAndRequestId(
                            conversationId,
                            requestId
                    )
                    .isPresent();

            if (duplicateExists) {
                return TurnCreationResult.alreadyExists();
            }

            throw exception;
        }
    }

    @Transactional
    public void markTranscribed(
            Long turnId,
            String provider,
            String model
    ) {
        CompanionTurn turn = getTurn(turnId);

        turn.markTranscribed(
                provider,
                model
        );
    }

    @Transactional
    public void markSafetyResult(
            Long turnId,
            SafetyType type,
            String ruleId
    ) {
        CompanionTurn turn = getTurn(turnId);

        turn.markSafetyResult(
                type,
                ruleId
        );
    }

    @Transactional
    public void markResponseGenerated(
            Long turnId,
            String provider,
            String model,
            String promptVersion,
            Integer inputTokens,
            Integer outputTokens
    ) {
        CompanionTurn turn = getTurn(turnId);

        turn.markResponseGenerated(
                provider,
                model,
                promptVersion,
                inputTokens,
                outputTokens
        );
    }

    @Transactional
    public void markCompleted(
            Long turnId,
            String ttsProvider,
            String ttsVoice
    ) {
        CompanionTurn turn = getTurn(turnId);

        turn.markCompleted(
                ttsProvider,
                ttsVoice
        );
    }

    @Transactional
    public void markFailed(
            Long turnId,
            FailureStage failureStage
    ) {
        CompanionTurn turn = getTurn(turnId);

        turn.markFailed(failureStage);
    }

    @Transactional
    public void prepareRetry(Long turnId) {
        CompanionTurn turn = getTurn(turnId);

        turn.prepareRetry();
    }

    @Transactional
    public void saveMessage(
            Long turnId,
            MessageRole role,
            String plaintext
    ) {
        CompanionTurn turn = getTurn(turnId);

        String encryptedContent = textCipher.encrypt(plaintext);

        CompanionMessage message = CompanionMessage.create(
                turn,
                role,
                encryptedContent
        );

        messageRepository.save(message);
    }

    @Transactional(readOnly = true)
    public List<CompanionMessageContent>
    loadRecentMessages(
            Long conversationId,
            int limit
    ) {
        if (limit <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        if (!conversationRepository.existsById(conversationId)) {
            throw new BusinessException(ErrorCode.COMPANION_CONVERSATION_NOT_FOUND);
        }

        int actualLimit = Math.min(
                limit,
                MAX_RECENT_MESSAGE_LIMIT
        );

        List<CompanionMessage> latestFirst = messageRepository.findByConversationConversationIdOrderByMessageIdDesc(
                conversationId,
                PageRequest.of(
                        0,
                        actualLimit
                )
        );

        List<CompanionMessage> oldestFirst = new ArrayList<>(latestFirst);

        Collections.reverse(oldestFirst);

        return oldestFirst.stream()
                .map(message -> new CompanionMessageContent(
                                message.getRole(),
                                textCipher.decrypt(message.getEncryptedContent()
                                )
                        )
                )
                .toList();
    }

    private CompanionTurn getTurn(Long turnId) {
        if (turnId == null) {
            throw new BusinessException(ErrorCode.COMPANION_TURN_NOT_FOUND);
        }

        return turnRepository.findById(turnId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPANION_TURN_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<CompanionContextMessage>
    loadRecentMessagesByUser(
            Long userId,
            int limit
    ) {
        if (userId == null || limit <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        int actualLimit = Math.min(
                limit,
                MAX_RECENT_MESSAGE_LIMIT
        );

        List<CompanionMessage> latestFirst =
                messageRepository
                        .findByConversationUserUsersIdOrderByMessageIdDesc(
                                userId,
                                PageRequest.of(
                                        0,
                                        actualLimit
                                )
                        );

        List<CompanionMessage> oldestFirst = new ArrayList<>(latestFirst);

        Collections.reverse(oldestFirst);

        return oldestFirst.stream()
                .map(message ->
                        new CompanionContextMessage(
                                message.getRole(),
                                textCipher.decrypt(
                                        message
                                                .getEncryptedContent()
                                ),
                                message.getCreatedAt()
                        )
                )
                .toList();
    }
}