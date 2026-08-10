package com.example.senioron.domain.companion.repository;

import com.example.senioron.domain.companion.entity.CompanionTurn;
import com.example.senioron.domain.companion.entity.TurnStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface CompanionTurnRepository extends JpaRepository<CompanionTurn, Long> {

    Optional<CompanionTurn>
    findByConversationConversationIdAndRequestId(
            Long conversationId,
            String requestId
    );

    boolean existsByConversationConversationIdAndStatusIn(
            Long conversationId,
            Collection<TurnStatus> statuses
    );
}
