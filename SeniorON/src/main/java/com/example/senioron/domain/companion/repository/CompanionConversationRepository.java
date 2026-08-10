package com.example.senioron.domain.companion.repository;

import com.example.senioron.domain.companion.entity.CompanionConversation;
import com.example.senioron.domain.companion.entity.ConversationStatus;
import com.example.senioron.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CompanionConversationRepository extends JpaRepository<CompanionConversation, Long> {

    Optional<CompanionConversation>
    findFirstByUserAndStatusOrderByCreatedAtDesc(
            User user,
            ConversationStatus status
    );

    long countByUserAndStatus(
            User user,
            ConversationStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(
            @QueryHint(
                    name = "jakarta.persistence.lock.timeout",
                    value = "5000"
            )
    )
    @Query("""
            SELECT c
            FROM CompanionConversation c
            WHERE c.conversationId = :conversationId
            """)
    Optional<CompanionConversation> findByIdForUpdate(
            @Param("conversationId") Long conversationId
    );
}
