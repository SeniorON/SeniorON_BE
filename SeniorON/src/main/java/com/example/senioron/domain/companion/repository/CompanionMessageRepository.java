package com.example.senioron.domain.companion.repository;

import com.example.senioron.domain.companion.entity.CompanionMessage;
import com.example.senioron.domain.companion.entity.MessageRole;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanionMessageRepository extends JpaRepository<CompanionMessage,Long> {

    List<CompanionMessage>
    findByConversationConversationIdOrderByMessageIdDesc(
            Long conversationId,
            Pageable pageable
    );

    List<CompanionMessage>
    findByConversationUserUsersIdOrderByMessageIdDesc(
            Long userId,
            Pageable pageable
    );

    Optional<CompanionMessage> findByTurnTurnIdAndRole(
            Long turnId,
            MessageRole role
    );

    List<CompanionMessage>
    findByConversationConversationIdAndTurnTurnIdNotOrderByMessageIdDesc(
            Long conversationId,
            Long turnId,
            Pageable pageable
    );
}
