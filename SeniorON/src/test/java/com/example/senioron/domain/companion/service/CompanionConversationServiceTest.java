package com.example.senioron.domain.companion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
import java.time.LocalDateTime;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CompanionConversationServiceTest {

    private static final Long PARENT_ID = 1L;
    private static final Long OTHER_PARENT_ID = 2L;
    private static final Long CHILD_ID = 3L;
    private static final Long CONVERSATION_ID = 10L;

    private final UserRepository userRepository =
            Mockito.mock(UserRepository.class);

    private final CompanionConversationRepository
            conversationRepository =
            Mockito.mock(
                    CompanionConversationRepository.class
            );

    private CompanionConversationService
            conversationService;

    @BeforeEach
    void setUp() {
        conversationService =
                new CompanionConversationService(
                        userRepository,
                        conversationRepository
                );
    }

    @Test
    void parentStartsNewConversation() {
        User parent =
                createParent(PARENT_ID);

        CompanionConversation savedConversation =
                createActiveConversation(
                        CONVERSATION_ID,
                        parent
                );

        given(
                userRepository.findByIdForUpdate(
                        PARENT_ID
                )
        ).willReturn(
                Optional.of(parent)
        );

        given(
                conversationRepository
                        .findFirstByUserAndStatusOrderByCreatedAtDesc(
                                parent,
                                ConversationStatus.ACTIVE
                        )
        ).willReturn(
                Optional.empty()
        );

        given(
                conversationRepository.save(
                        any(CompanionConversation.class)
                )
        ).willReturn(
                savedConversation
        );

        CompanionConversationStartResponse response =
                conversationService.start(parent);

        assertThat(response.getConversationId())
                .isEqualTo(CONVERSATION_ID);

        assertThat(response.getConversationStatus())
                .isEqualTo(
                        ConversationStatus.ACTIVE
                );

        assertThat(response.isCreated())
                .isTrue();

        verify(userRepository)
                .findByIdForUpdate(PARENT_ID);

        verify(userRepository, never())
                .findById(PARENT_ID);

        verify(conversationRepository)
                .save(
                        any(CompanionConversation.class)
                );
    }

    @Test
    void parentReusesExistingActiveConversation() {
        User parent =
                createParent(PARENT_ID);

        CompanionConversation existingConversation =
                createActiveConversation(
                        CONVERSATION_ID,
                        parent
                );

        given(
                userRepository.findByIdForUpdate(
                        PARENT_ID
                )
        ).willReturn(
                Optional.of(parent)
        );

        given(
                conversationRepository
                        .findFirstByUserAndStatusOrderByCreatedAtDesc(
                                parent,
                                ConversationStatus.ACTIVE
                        )
        ).willReturn(
                Optional.of(existingConversation)
        );

        CompanionConversationStartResponse response =
                conversationService.start(parent);

        assertThat(response.getConversationId())
                .isEqualTo(CONVERSATION_ID);

        assertThat(response.getConversationStatus())
                .isEqualTo(
                        ConversationStatus.ACTIVE
                );

        assertThat(response.isCreated())
                .isFalse();

        verify(conversationRepository, never())
                .save(
                        any(CompanionConversation.class)
                );
    }

    @Test
    void childCannotStartConversation() {
        User child =
                createChild(CHILD_ID);

        given(
                userRepository.findByIdForUpdate(
                        CHILD_ID
                )
        ).willReturn(
                Optional.of(child)
        );

        assertBusinessException(
                () -> conversationService.start(child),
                ErrorCode.COMPANION_PARENT_ONLY
        );

        verify(conversationRepository, never())
                .findFirstByUserAndStatusOrderByCreatedAtDesc(
                        any(User.class),
                        any(ConversationStatus.class)
                );

        verify(conversationRepository, never())
                .save(
                        any(CompanionConversation.class)
                );
    }

    @Test
    void withdrawnParentCannotStartConversation() {
        User withdrawnParent =
                createWithdrawnParent(PARENT_ID);

        given(
                userRepository.findByIdForUpdate(
                        PARENT_ID
                )
        ).willReturn(
                Optional.of(withdrawnParent)
        );

        assertBusinessException(
                () ->
                        conversationService.start(
                                withdrawnParent
                        ),
                ErrorCode.USER_NOT_AUTHENTICATED
        );

        verify(conversationRepository, never())
                .save(
                        any(CompanionConversation.class)
                );
    }

    @Test
    void missingPrincipalCannotStartConversation() {
        assertBusinessException(
                () -> conversationService.start(null),
                ErrorCode.USER_NOT_AUTHENTICATED
        );

        verify(userRepository, never())
                .findByIdForUpdate(
                        any(Long.class)
                );
    }

    @Test
    void principalWithoutIdCannotStartConversation() {
        User principalWithoutId =
                User.builder()
                        .role(Role.PARENT)
                        .status(UserStatus.ACTIVE)
                        .build();

        assertBusinessException(
                () ->
                        conversationService.start(
                                principalWithoutId
                        ),
                ErrorCode.USER_NOT_AUTHENTICATED
        );

        verify(userRepository, never())
                .findByIdForUpdate(
                        any(Long.class)
                );
    }

    @Test
    void missingCurrentUserCannotStartConversation() {
        User principal =
                createParent(PARENT_ID);

        given(
                userRepository.findByIdForUpdate(
                        PARENT_ID
                )
        ).willReturn(
                Optional.empty()
        );

        assertBusinessException(
                () -> conversationService.start(principal),
                ErrorCode.USER_NOT_FOUND
        );

        verify(conversationRepository, never())
                .save(
                        any(CompanionConversation.class)
                );
    }

    @Test
    void parentEndsOwnConversation() {
        User parent =
                createParent(PARENT_ID);

        CompanionConversation conversation =
                createActiveConversation(
                        CONVERSATION_ID,
                        parent
                );

        given(
                userRepository.findById(PARENT_ID)
        ).willReturn(
                Optional.of(parent)
        );

        given(
                conversationRepository.findByIdForUpdate(CONVERSATION_ID)
        ).willReturn(
                Optional.of(conversation)
        );

        CompanionConversationEndResponse response =
                conversationService.end(
                        parent,
                        CONVERSATION_ID
                );

        assertThat(response.getConversationId())
                .isEqualTo(CONVERSATION_ID);

        assertThat(response.getConversationStatus())
                .isEqualTo(
                        ConversationStatus.ENDED
                );

        assertThat(response.getEndedAt())
                .isNotNull();

        assertThat(conversation.getStatus())
                .isEqualTo(
                        ConversationStatus.ENDED
                );

        assertThat(conversation.getEndedAt())
                .isNotNull();
    }

    @Test
    void parentCannotEndAnotherParentsConversation() {
        User currentParent =
                createParent(PARENT_ID);

        User otherParent =
                createParent(OTHER_PARENT_ID);

        CompanionConversation otherConversation =
                createActiveConversation(
                        CONVERSATION_ID,
                        otherParent
                );

        given(
                userRepository.findById(PARENT_ID)
        ).willReturn(
                Optional.of(currentParent)
        );

        given(
                conversationRepository.findByIdForUpdate(CONVERSATION_ID)
        ).willReturn(
                Optional.of(otherConversation)
        );

        assertBusinessException(
                () ->
                        conversationService.end(
                                currentParent,
                                CONVERSATION_ID
                        ),
                ErrorCode
                        .COMPANION_CONVERSATION_FORBIDDEN
        );

        assertThat(otherConversation.getStatus())
                .isEqualTo(
                        ConversationStatus.ACTIVE
                );

        assertThat(otherConversation.getEndedAt())
                .isNull();
    }

    @Test
    void endingMissingConversationReturnsNotFound() {
        User parent =
                createParent(PARENT_ID);

        given(
                userRepository.findById(PARENT_ID)
        ).willReturn(
                Optional.of(parent)
        );

        given(
                conversationRepository.findByIdForUpdate(CONVERSATION_ID)
        ).willReturn(
                Optional.empty()
        );

        assertBusinessException(
                () ->
                        conversationService.end(
                                parent,
                                CONVERSATION_ID
                        ),
                ErrorCode
                        .COMPANION_CONVERSATION_NOT_FOUND
        );
    }

    @Test
    void endingNullConversationIdReturnsNotFound() {
        User parent =
                createParent(PARENT_ID);

        given(
                userRepository.findById(PARENT_ID)
        ).willReturn(
                Optional.of(parent)
        );

        assertBusinessException(
                () ->
                        conversationService.end(
                                parent,
                                null
                        ),
                ErrorCode
                        .COMPANION_CONVERSATION_NOT_FOUND
        );

        verify(conversationRepository, never())
                .findByIdForUpdate(any());
    }

    @Test
    void endingConversationTwiceKeepsOriginalEndedAt() {
        User parent =
                createParent(PARENT_ID);

        CompanionConversation conversation =
                createActiveConversation(
                        CONVERSATION_ID,
                        parent
                );

        conversation.end();

        LocalDateTime firstEndedAt =
                conversation.getEndedAt();

        given(
                userRepository.findById(PARENT_ID)
        ).willReturn(
                Optional.of(parent)
        );

        given(
                conversationRepository.findByIdForUpdate(CONVERSATION_ID)
        ).willReturn(
                Optional.of(conversation)
        );

        CompanionConversationEndResponse response =
                conversationService.end(
                        parent,
                        CONVERSATION_ID
                );

        assertThat(response.getConversationStatus())
                .isEqualTo(
                        ConversationStatus.ENDED
                );

        assertThat(response.getEndedAt())
                .isEqualTo(firstEndedAt);

        assertThat(conversation.getEndedAt())
                .isEqualTo(firstEndedAt);
    }

    @Test
    void returnsOwnedActiveUserId() {
        User parent =
                createParent(PARENT_ID);

        CompanionConversation conversation =
                createActiveConversation(
                        CONVERSATION_ID,
                        parent
                );

        given(
                userRepository.findById(PARENT_ID)
        ).willReturn(
                Optional.of(parent)
        );

        given(
                conversationRepository.findById(
                        CONVERSATION_ID
                )
        ).willReturn(
                Optional.of(conversation)
        );

        Long result =
                conversationService
                        .getOwnedActiveUserId(
                                parent,
                                CONVERSATION_ID
                        );

        assertThat(result)
                .isEqualTo(PARENT_ID);
    }

    @Test
    void endedConversationCannotBeLoadedAsActive() {
        User parent =
                createParent(PARENT_ID);

        CompanionConversation conversation =
                createActiveConversation(
                        CONVERSATION_ID,
                        parent
                );

        conversation.end();

        given(
                userRepository.findById(PARENT_ID)
        ).willReturn(
                Optional.of(parent)
        );

        given(
                conversationRepository.findById(CONVERSATION_ID)
        ).willReturn(
                Optional.of(conversation)
        );

        assertBusinessException(
                () ->
                        conversationService
                                .getOwnedActiveUserId(
                                        parent,
                                        CONVERSATION_ID
                                ),
                ErrorCode.COMPANION_CONVERSATION_ENDED
        );
    }

    @Test
    void anotherParentsConversationCannotBeLoadedAsActive() {
        User currentParent =
                createParent(PARENT_ID);

        User otherParent =
                createParent(OTHER_PARENT_ID);

        CompanionConversation conversation =
                createActiveConversation(
                        CONVERSATION_ID,
                        otherParent
                );

        given(
                userRepository.findById(PARENT_ID)
        ).willReturn(
                Optional.of(currentParent)
        );

        given(
                conversationRepository.findById(CONVERSATION_ID)
        ).willReturn(
                Optional.of(conversation)
        );

        assertBusinessException(
                () ->
                        conversationService
                                .getOwnedActiveUserId(
                                        currentParent,
                                        CONVERSATION_ID
                                ),
                ErrorCode
                        .COMPANION_CONVERSATION_FORBIDDEN
        );
    }

    private User createParent(Long userId) {
        return User.builder()
                .usersId(userId)
                .name("테스트 부모")
                .role(Role.PARENT)
                .status(UserStatus.ACTIVE)
                .build();
    }

    private User createChild(Long userId) {
        return User.builder()
                .usersId(userId)
                .name("테스트 자녀")
                .role(Role.CHILD)
                .status(UserStatus.ACTIVE)
                .build();
    }

    private User createWithdrawnParent(
            Long userId
    ) {
        return User.builder()
                .usersId(userId)
                .name("탈퇴한 부모")
                .role(Role.PARENT)
                .status(UserStatus.WITHDRAWN)
                .build();
    }

    private CompanionConversation
    createActiveConversation(
            Long conversationId,
            User owner
    ) {
        return CompanionConversation.builder()
                .conversationId(conversationId)
                .user(owner)
                .status(ConversationStatus.ACTIVE)
                .build();
    }

    private void assertBusinessException(
            ThrowingCallable operation,
            ErrorCode expectedErrorCode
    ) {
        assertThatThrownBy(operation)
                .isInstanceOf(
                        BusinessException.class
                )
                .satisfies(exception -> {
                    BusinessException businessException =
                            (BusinessException) exception;

                    assertThat(
                            businessException.getCode()
                    ).isEqualTo(
                            expectedErrorCode
                    );
                });
    }
}