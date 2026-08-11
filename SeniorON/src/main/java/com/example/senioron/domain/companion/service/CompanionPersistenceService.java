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
import com.example.senioron.domain.companion.service.model.CompanionTurnSnapshot;
import com.example.senioron.domain.companion.service.model.TranscriptionResult;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompanionPersistenceService {

    private static final int MAX_RECENT_MESSAGE_LIMIT = 20;

    private final CompanionConversationRepository conversationRepository;

    private final CompanionTurnRepository turnRepository;

    private final CompanionMessageRepository messageRepository;

    private final CompanionTextCipher textCipher;



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

    @Transactional
    public void saveTranscription(
            Long turnId,
            TranscriptionResult result
    ) {
        if (result == null) {
            throw new BusinessException(
                    ErrorCode
                            .COMPANION_PERSISTENCE_FAILED
            );
        }

        CompanionTurn turn =
                getTurn(turnId);

        turn.markTranscribed(
                result.provider(),
                result.model()
        );

        saveEncryptedMessage(
                turn,
                MessageRole.USER,
                result.text()
        );
    }

    @Transactional
    public void saveGeneratedResponse(
            Long turnId,
            String assistantText,
            String provider,
            String model,
            String promptVersion,
            Integer inputTokens,
            Integer outputTokens
    ) {
        CompanionTurn turn =
                getTurn(turnId);

        turn.markResponseGenerated(
                provider,
                model,
                promptVersion,
                inputTokens,
                outputTokens
        );

        saveEncryptedMessage(
                turn,
                MessageRole.ASSISTANT,
                assistantText
        );
    }

    private void saveEncryptedMessage(
            CompanionTurn turn,
            MessageRole role,
            String plaintext
    ) {
        String encryptedContent =
                textCipher.encrypt(plaintext);

        CompanionMessage message =
                CompanionMessage.create(
                        turn,
                        role,
                        encryptedContent
                );

        messageRepository.save(message);
    }

    @Transactional(readOnly = true)
    public CompanionTurnSnapshot loadTurnSnapshot(
            Long turnId
    ) {
        CompanionTurn turn =
                getTurn(turnId);

        String transcript =
                loadMessageOrNull(
                        turnId,
                        MessageRole.USER
                );

        String assistantText =
                loadMessageOrNull(
                        turnId,
                        MessageRole.ASSISTANT
                );

        return new CompanionTurnSnapshot(
                turn.getConversation()
                        .getConversationId(),
                turn.getTurnId(),
                turn.getStatus(),
                turn.getFailureStage(),
                turn.getSafetyType(),
                transcript,
                assistantText
        );
    }

    private String loadMessageOrNull(
            Long turnId,
            MessageRole role
    ) {
        return messageRepository
                .findByTurnTurnIdAndRole(
                        turnId,
                        role
                )
                .map(
                        CompanionMessage
                                ::getEncryptedContent
                )
                .map(textCipher::decrypt)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<CompanionMessageContent>
    loadRecentMessagesExcludingTurn(
            Long conversationId,
            Long turnId,
            int limit
    ) {
        if (conversationId == null
                || turnId == null
                || limit <= 0) {

            throw new BusinessException(
                    ErrorCode.BAD_REQUEST
            );
        }

        int actualLimit =
                Math.min(
                        limit,
                        MAX_RECENT_MESSAGE_LIMIT
                );

        List<CompanionMessage> latestFirst =
                messageRepository
                        .findByConversationConversationIdAndTurnTurnIdNotOrderByMessageIdDesc(
                                conversationId,
                                turnId,
                                PageRequest.of(
                                        0,
                                        actualLimit
                                )
                        );

        List<CompanionMessage> oldestFirst =
                new ArrayList<>(latestFirst);

        Collections.reverse(oldestFirst);

        return oldestFirst.stream()
                .map(message ->
                        new CompanionMessageContent(
                                message.getRole(),
                                textCipher.decrypt(
                                        message
                                                .getEncryptedContent()
                                )
                        )
                )
                .toList();
    }
}