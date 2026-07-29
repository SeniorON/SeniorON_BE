package com.example.senioron.domain.companion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.senioron.domain.companion.crypto.CompanionTextCipher;
import com.example.senioron.domain.companion.entity.CompanionConversation;
import com.example.senioron.domain.companion.entity.CompanionMessage;
import com.example.senioron.domain.companion.entity.CompanionTurn;
import com.example.senioron.domain.companion.entity.ConversationStatus;
import com.example.senioron.domain.companion.entity.MessageRole;
import com.example.senioron.domain.companion.entity.TurnStatus;
import com.example.senioron.domain.companion.repository.CompanionConversationRepository;
import com.example.senioron.domain.companion.repository.CompanionMessageRepository;
import com.example.senioron.domain.companion.repository.CompanionTurnRepository;
import com.example.senioron.domain.companion.service.model.CompanionMessageContent;
import com.example.senioron.domain.companion.service.model.TurnCreationResult;
import com.example.senioron.domain.companion.service.model.TurnCreationStatus;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.entity.UserStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;

class CompanionPersistenceServiceTest {

    private static final Long CONVERSATION_ID = 1L;
    private static final Long TURN_ID = 10L;

    private final CompanionConversationRepository
            conversationRepository =
            Mockito.mock(
                    CompanionConversationRepository.class
            );

    private final CompanionTurnRepository turnRepository =
            Mockito.mock(
                    CompanionTurnRepository.class
            );

    private final CompanionMessageRepository
            messageRepository =
            Mockito.mock(
                    CompanionMessageRepository.class
            );

    private final CompanionTurnCreationTransactionService
            turnCreationTransactionService =
            Mockito.mock(
                    CompanionTurnCreationTransactionService.class
            );

    private final CompanionTextCipher textCipher =
            Mockito.mock(CompanionTextCipher.class);

    private CompanionPersistenceService service;

    @BeforeEach
    void setUp() {
        service =
                new CompanionPersistenceService(
                        conversationRepository,
                        turnRepository,
                        messageRepository,
                        turnCreationTransactionService,
                        textCipher
                );
    }

    @Test
    void createsNewTurn() {
        String requestId =
                UUID.randomUUID().toString();

        given(
                turnRepository
                        .findByConversationConversationIdAndRequestId(
                                CONVERSATION_ID,
                                requestId
                        )
        ).willReturn(Optional.empty());

        given(
                turnCreationTransactionService.create(
                        CONVERSATION_ID,
                        requestId
                )
        ).willReturn(TURN_ID);

        TurnCreationResult result =
                service.createTurn(
                        CONVERSATION_ID,
                        requestId
                );

        assertThat(result.status())
                .isEqualTo(
                        TurnCreationStatus.CREATED
                );

        assertThat(result.createdTurnId())
                .isEqualTo(TURN_ID);
    }

    @Test
    void returnsAlreadyExistsForExistingRequestId() {
        String requestId =
                UUID.randomUUID().toString();

        CompanionTurn existing =
                createTurn(requestId);

        given(
                turnRepository
                        .findByConversationConversationIdAndRequestId(
                                CONVERSATION_ID,
                                requestId
                        )
        ).willReturn(Optional.of(existing));

        TurnCreationResult result =
                service.createTurn(
                        CONVERSATION_ID,
                        requestId
                );

        assertThat(result.status())
                .isEqualTo(
                        TurnCreationStatus.ALREADY_EXISTS
                );

        assertThat(result.createdTurnId())
                .isNull();

        verify(
                turnCreationTransactionService,
                never()
        ).create(
                CONVERSATION_ID,
                requestId
        );
    }

    @Test
    void convertsUniqueConflictToAlreadyExists() {
        String requestId =
                UUID.randomUUID().toString();

        CompanionTurn existing =
                createTurn(requestId);

        given(
                turnRepository
                        .findByConversationConversationIdAndRequestId(
                                CONVERSATION_ID,
                                requestId
                        )
        ).willReturn(
                Optional.empty(),
                Optional.of(existing)
        );

        given(
                turnCreationTransactionService.create(
                        CONVERSATION_ID,
                        requestId
                )
        ).willThrow(
                new DataIntegrityViolationException(
                        "duplicate request"
                )
        );

        TurnCreationResult result =
                service.createTurn(
                        CONVERSATION_ID,
                        requestId
                );

        assertThat(result.status())
                .isEqualTo(
                        TurnCreationStatus.ALREADY_EXISTS
                );

        assertThat(result.createdTurnId())
                .isNull();
    }

    @Test
    void encryptsPlaintextBeforeSavingMessage() {
        CompanionTurn turn =
                createTurn(
                        UUID.randomUUID().toString()
                );

        String plaintext =
                "안녕하세요";

        String encrypted =
                "v1:test-iv:test-ciphertext";

        given(
                turnRepository.findById(TURN_ID)
        ).willReturn(Optional.of(turn));

        given(
                textCipher.encrypt(plaintext)
        ).willReturn(encrypted);

        service.saveMessage(
                TURN_ID,
                MessageRole.USER,
                plaintext
        );

        ArgumentCaptor<CompanionMessage> captor =
                ArgumentCaptor.forClass(
                        CompanionMessage.class
                );

        verify(messageRepository)
                .save(captor.capture());

        assertThat(
                captor.getValue()
                        .getEncryptedContent()
        ).isEqualTo(encrypted);

        assertThat(
                captor.getValue()
                        .getEncryptedContent()
        ).doesNotContain(plaintext);
    }

    @Test
    void loadsLatestMessagesOldestFirstAndCapsLimitAtTwenty() {
        CompanionConversation conversation =
                createConversation();

        CompanionTurn turn =
                createTurn(
                        UUID.randomUUID().toString()
                );

        CompanionMessage newest =
                createMessage(
                        3L,
                        conversation,
                        turn,
                        MessageRole.USER,
                        "encrypted-3"
                );

        CompanionMessage middle =
                createMessage(
                        2L,
                        conversation,
                        turn,
                        MessageRole.ASSISTANT,
                        "encrypted-2"
                );

        CompanionMessage oldest =
                createMessage(
                        1L,
                        conversation,
                        turn,
                        MessageRole.USER,
                        "encrypted-1"
                );

        given(
                conversationRepository.existsById(
                        CONVERSATION_ID
                )
        ).willReturn(true);

        given(
                messageRepository
                        .findByConversationConversationIdOrderByMessageIdDesc(
                                eq(CONVERSATION_ID),
                                any(Pageable.class)
                        )
        ).willReturn(
                List.of(
                        newest,
                        middle,
                        oldest
                )
        );

        given(
                textCipher.decrypt("encrypted-1")
        ).willReturn("첫 번째");

        given(
                textCipher.decrypt("encrypted-2")
        ).willReturn("두 번째");

        given(
                textCipher.decrypt("encrypted-3")
        ).willReturn("세 번째");

        List<CompanionMessageContent> result =
                service.loadRecentMessages(
                        CONVERSATION_ID,
                        100
                );

        assertThat(result)
                .extracting(
                        CompanionMessageContent::content
                )
                .containsExactly(
                        "첫 번째",
                        "두 번째",
                        "세 번째"
                );

        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(
                        Pageable.class
                );

        verify(messageRepository)
                .findByConversationConversationIdOrderByMessageIdDesc(
                        eq(CONVERSATION_ID),
                        pageableCaptor.capture()
                );

        assertThat(
                pageableCaptor.getValue()
                        .getPageSize()
        ).isEqualTo(20);
    }

    private CompanionTurn createTurn(
            String requestId
    ) {
        return CompanionTurn.builder()
                .turnId(TURN_ID)
                .conversation(
                        createConversation()
                )
                .requestId(requestId)
                .status(TurnStatus.RECEIVED)
                .build();
    }

    private CompanionConversation
    createConversation() {
        User parent =
                User.builder()
                        .usersId(1L)
                        .name("테스트 부모")
                        .role(Role.PARENT)
                        .status(UserStatus.ACTIVE)
                        .build();

        return CompanionConversation.builder()
                .conversationId(CONVERSATION_ID)
                .user(parent)
                .status(ConversationStatus.ACTIVE)
                .build();
    }

    private CompanionMessage createMessage(
            Long messageId,
            CompanionConversation conversation,
            CompanionTurn turn,
            MessageRole role,
            String encryptedContent
    ) {
        return CompanionMessage.builder()
                .messageId(messageId)
                .conversation(conversation)
                .turn(turn)
                .role(role)
                .encryptedContent(encryptedContent)
                .build();
    }
}