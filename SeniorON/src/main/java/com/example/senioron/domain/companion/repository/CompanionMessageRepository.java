package com.example.senioron.domain.companion.repository;

import com.example.senioron.domain.companion.entity.CompanionMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

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
}
