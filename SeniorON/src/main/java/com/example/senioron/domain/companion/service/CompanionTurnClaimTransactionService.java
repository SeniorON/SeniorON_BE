package com.example.senioron.domain.companion.service;

import com.example.senioron.domain.companion.entity.CompanionConversation;
import com.example.senioron.domain.companion.entity.CompanionTurn;
import com.example.senioron.domain.companion.entity.FailureStage;
import com.example.senioron.domain.companion.entity.TurnStatus;
import com.example.senioron.domain.companion.repository.CompanionConversationRepository;
import com.example.senioron.domain.companion.repository.CompanionTurnRepository;
import com.example.senioron.domain.companion.service.model.TurnClaimResult;
import com.example.senioron.domain.companion.service.model.TurnClaimStatus;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CompanionTurnClaimTransactionService {

    private static final Set<TurnStatus>
            PROCESSING_STATUSES =
            EnumSet.of(
                    TurnStatus.RECEIVED,
                    TurnStatus.TRANSCRIBED,
                    TurnStatus.RESPONSE_GENERATED
            );

    private final CompanionConversationRepository
            conversationRepository;

    private final CompanionTurnRepository
            turnRepository;

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            timeout = 5
    )
    public TurnClaimResult claim(
            Long conversationId,
            Long userId,
            String requestId
    ) {
        CompanionConversation conversation =
                lockConversation(conversationId);

        if (!conversation.isOwnedBy(userId)) {
            throw new BusinessException(
                    ErrorCode
                            .COMPANION_CONVERSATION_FORBIDDEN
            );
        }

        if (!conversation.isActive()) {
            throw new BusinessException(
                    ErrorCode
                            .COMPANION_CONVERSATION_ENDED
            );
        }

        Optional<CompanionTurn> existingTurn =
                turnRepository
                        .findByConversationConversationIdAndRequestId(
                                conversationId,
                                requestId
                        );

        if (existingTurn.isPresent()) {
            return claimExistingTurn(
                    existingTurn.get()
            );
        }

        boolean anotherTurnProcessing =
                turnRepository
                        .existsByConversationConversationIdAndStatusIn(
                                conversationId,
                                PROCESSING_STATUSES
                        );

        if (anotherTurnProcessing) {
            throw new BusinessException(
                    ErrorCode
                            .COMPANION_TURN_IN_PROGRESS
            );
        }

        CompanionTurn turn =
                CompanionTurn.receive(
                        conversation,
                        requestId
                );

        CompanionTurn saved =
                turnRepository.saveAndFlush(turn);

        return new TurnClaimResult(
                TurnClaimStatus.CREATED,
                saved.getTurnId(),
                saved.getStatus(),
                null
        );
    }

    private CompanionConversation lockConversation(
            Long conversationId
    ) {
        try {
            return conversationRepository
                    .findByIdForUpdate(
                            conversationId
                    )
                    .orElseThrow(() ->
                            new BusinessException(
                                    ErrorCode
                                            .COMPANION_CONVERSATION_NOT_FOUND
                            )
                    );
        } catch (PessimisticLockingFailureException exception) {
            throw new BusinessException(
                    ErrorCode
                            .COMPANION_TURN_IN_PROGRESS
            );
        }
    }

    private TurnClaimResult claimExistingTurn(
            CompanionTurn turn
    ) {
        if (turn.getStatus()
                == TurnStatus.COMPLETED) {

            return new TurnClaimResult(
                    TurnClaimStatus.COMPLETED,
                    turn.getTurnId(),
                    turn.getStatus(),
                    null
            );
        }

        if (turn.getStatus()
                == TurnStatus.FAILED) {

            FailureStage retryStage =
                    turn.getFailureStage();

            if (retryStage
                    == FailureStage.PERSISTENCE) {

                throw new BusinessException(
                        ErrorCode
                                .COMPANION_PERSISTENCE_FAILED
                );
            }

            turn.prepareRetry();

            return new TurnClaimResult(
                    TurnClaimStatus.RETRY,
                    turn.getTurnId(),
                    turn.getStatus(),
                    retryStage
            );
        }

        return new TurnClaimResult(
                TurnClaimStatus.PROCESSING,
                turn.getTurnId(),
                turn.getStatus(),
                null
        );
    }
}
