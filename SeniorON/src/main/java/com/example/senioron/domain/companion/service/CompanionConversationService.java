package com.example.senioron.domain.companion.service;

import com.example.senioron.domain.companion.dto.response.CompanionConversationEndResponse;
import com.example.senioron.domain.companion.dto.response.CompanionConversationStartResponse;
import com.example.senioron.domain.companion.entity.CompanionConversation;
import com.example.senioron.domain.companion.entity.ConversationStatus;
import com.example.senioron.domain.companion.repository.CompanionConversationRepository;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.entity.UserStatus;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompanionConversationService {

    private final UserRepository userRepository;
    private final CompanionConversationRepository conversationRepository;

    private User getCurrentParent(User principal) {
        if (principal == null
                || principal.getUsersId() == null) {
            throw new BusinessException(
                    ErrorCode.USER_NOT_AUTHENTICATED
            );
        }

        User currentUser =
                userRepository.findById(
                        principal.getUsersId()
                ).orElseThrow(() ->
                        new BusinessException(
                                ErrorCode.USER_NOT_FOUND
                        )
                );

        validateParent(currentUser);

        return currentUser;
    }

    private User getLockedCurrentParent(
            User principal
    ) {
        if (principal == null
                || principal.getUsersId() == null) {
            throw new BusinessException(
                    ErrorCode.USER_NOT_AUTHENTICATED
            );
        }

        User currentUser =
                userRepository.findByIdForUpdate(
                        principal.getUsersId()
                ).orElseThrow(() ->
                        new BusinessException(
                                ErrorCode.USER_NOT_FOUND
                        )
                );

        validateParent(currentUser);

        return currentUser;
    }

    private void validateParent(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(
                    ErrorCode.USER_NOT_AUTHENTICATED
            );
        }

        if (user.getRole() != Role.PARENT) {
            throw new BusinessException(
                    ErrorCode.COMPANION_PARENT_ONLY
            );
        }
    }

    @Transactional
    public CompanionConversationStartResponse start(
            User principal
    ) {
        User user =
                getLockedCurrentParent(principal);

        Optional<CompanionConversation> existing =
                conversationRepository
                        .findFirstByUserAndStatusOrderByCreatedAtDesc(
                                user,
                                ConversationStatus.ACTIVE
                        );

        if (existing.isPresent()) {
            return toStartResponse(
                    existing.get(),
                    false
            );
        }

        CompanionConversation conversation =
                CompanionConversation.start(user);

        CompanionConversation saved =
                conversationRepository.save(conversation);

        return toStartResponse(saved, true);
    }

    private CompanionConversationStartResponse toStartResponse(
            CompanionConversation conversation,
            boolean created
    ) {
        return CompanionConversationStartResponse.builder()
                .conversationId(
                        conversation.getConversationId()
                )
                .conversationStatus(
                        conversation.getStatus()
                )
                .startedAt(
                        conversation.getCreatedAt()
                )
                .created(created)
                .build();
    }

    private CompanionConversation getConversationOrThrow(
            Long conversationId
    ) {
        if (conversationId == null) {
            throw new BusinessException(ErrorCode.COMPANION_CONVERSATION_NOT_FOUND);
        }

        return conversationRepository
                .findById(conversationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPANION_CONVERSATION_NOT_FOUND));
    }

    @Transactional
    public CompanionConversationEndResponse end(
            User principal,
            Long conversationId
    ) {
        User user = getCurrentParent(principal);

        CompanionConversation conversation =
                getConversationForUpdateOrThrow(conversationId);

        validateOwner(conversation, user);

        conversation.end();

        return CompanionConversationEndResponse.builder()
                .conversationId(
                        conversation.getConversationId()
                )
                .conversationStatus(
                        conversation.getStatus()
                )
                .endedAt(
                        conversation.getEndedAt()
                )
                .build();
    }


    private void validateOwner(
            CompanionConversation conversation,
            User user
    ) {
        if (!conversation.isOwnedBy(
                user.getUsersId()
        )) {
            throw new BusinessException(
                    ErrorCode
                            .COMPANION_CONVERSATION_FORBIDDEN
            );
        }
    }

    private CompanionConversation getConversationForUpdateOrThrow(
            Long conversationId
    ) {
        if (conversationId == null) {
            throw new BusinessException(
                    ErrorCode.COMPANION_CONVERSATION_NOT_FOUND
            );
        }

        return conversationRepository
                .findByIdForUpdate(conversationId)
                .orElseThrow(() ->
                        new BusinessException(
                                ErrorCode.COMPANION_CONVERSATION_NOT_FOUND
                        )
                );
    }

    public Long getOwnedActiveUserId(
            User principal,
            Long conversationId
    ) {
        User user = getCurrentParent(principal);

        CompanionConversation conversation = getConversationOrThrow(conversationId);

        validateOwner(
                conversation,
                user
        );

        if (!conversation.isActive()) {
            throw new BusinessException(ErrorCode.COMPANION_CONVERSATION_ENDED);
        }

        return user.getUsersId();
    }
}
