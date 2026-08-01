package com.example.senioron.domain.companion.service;

import com.example.senioron.domain.companion.client.llm.AnthropicConversationClient;
import com.example.senioron.domain.companion.config.CompanionPromptProvider;
import com.example.senioron.domain.companion.entity.CompanionConversation;
import com.example.senioron.domain.companion.entity.ConversationStatus;
import com.example.senioron.domain.companion.entity.MessageRole;
import com.example.senioron.domain.companion.service.model.CompanionContextMessage;
import com.example.senioron.domain.companion.service.model.CompanionReplyResult;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.entity.UserStatus;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CompanionReplyServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long CONVERSATION_ID =
            10L;

    private final CompanionConversationService
            conversationService =
            Mockito.mock(
                    CompanionConversationService.class
            );

    private final CompanionPersistenceService
            persistenceService =
            Mockito.mock(
                    CompanionPersistenceService.class
            );

    private final AnthropicConversationClient
            anthropicClient =
            Mockito.mock(
                    AnthropicConversationClient.class
            );

    private final CompanionPromptProvider
            promptProvider =
            Mockito.mock(
                    CompanionPromptProvider.class
            );

    private CompanionReplyService service;

    private User principal;

    private CompanionConversation conversation;

    @BeforeEach
    void setUp() {
        service =
                new CompanionReplyService(
                        conversationService,
                        persistenceService,
                        anthropicClient,
                        promptProvider
                );

        principal =
                User.builder()
                        .usersId(USER_ID)
                        .name("테스트 부모")
                        .role(Role.PARENT)
                        .status(UserStatus.ACTIVE)
                        .build();

        conversation =
                CompanionConversation.builder()
                        .conversationId(
                                CONVERSATION_ID
                        )
                        .user(principal)
                        .status(
                                ConversationStatus.ACTIVE
                        )
                        .build();
    }

    @Test
    void 사용자_최근_대화를_조회하여_Anthropic_답변을_반환한다() {
        List<CompanionContextMessage> messages =
                List.of(
                        new CompanionContextMessage(
                                MessageRole.USER,
                                "어제 공원에 갔어요.",
                                LocalDateTime.of(
                                        2026,
                                        7,
                                        31,
                                        18,
                                        30
                                )
                        ),
                        new CompanionContextMessage(
                                MessageRole.ASSISTANT,
                                "공원에 다녀오셨군요.",
                                LocalDateTime.of(
                                        2026,
                                        7,
                                        31,
                                        18,
                                        31
                                )
                        ),
                        new CompanionContextMessage(
                                MessageRole.USER,
                                "오늘도 가볼까요?",
                                LocalDateTime.of(
                                        2026,
                                        8,
                                        1,
                                        9,
                                        10
                                )
                        )
                );

        CompanionReplyResult expected =
                new CompanionReplyResult(
                        "오늘도 산책하기 좋겠어요.",
                        "ANTHROPIC",
                        "claude-haiku-4-5-20251001",
                        "v1",
                        120,
                        25
                );

        given(
                conversationService
                        .getOwnedActiveConversation(
                                principal,
                                CONVERSATION_ID
                        )
        ).willReturn(conversation);

        given(
                promptProvider
                        .getRecentMessageLimit()
        ).willReturn(20);

        given(
                persistenceService
                        .loadRecentMessagesByUser(
                                USER_ID,
                                20
                        )
        ).willReturn(messages);

        given(
                promptProvider.getSystemPrompt()
        ).willReturn(
                "테스트 시스템 프롬프트"
        );

        given(
                promptProvider.getVersion()
        ).willReturn("v1");

        given(
                anthropicClient.generateReply(
                        "테스트 시스템 프롬프트",
                        "v1",
                        messages
                )
        ).willReturn(expected);

        CompanionReplyResult result =
                service.generate(
                        principal,
                        CONVERSATION_ID
                );

        assertThat(result)
                .isSameAs(expected);

        verify(conversationService)
                .getOwnedActiveConversation(
                        principal,
                        CONVERSATION_ID
                );

        verify(persistenceService)
                .loadRecentMessagesByUser(
                        USER_ID,
                        20
                );

        verify(anthropicClient)
                .generateReply(
                        "테스트 시스템 프롬프트",
                        "v1",
                        messages
                );
    }

    @Test
    void 대화_소유권_검증에_실패하면_메시지와_Anthropic을_호출하지_않는다() {
        given(
                conversationService
                        .getOwnedActiveConversation(
                                principal,
                                CONVERSATION_ID
                        )
        ).willThrow(
                new BusinessException(
                        ErrorCode
                                .COMPANION_CONVERSATION_FORBIDDEN
                )
        );

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> service.generate(
                                principal,
                                CONVERSATION_ID
                        )
                );

        assertThat(exception.getCode())
                .isEqualTo(
                        ErrorCode
                                .COMPANION_CONVERSATION_FORBIDDEN
                );

        verify(
                persistenceService,
                never()
        ).loadRecentMessagesByUser(
                Mockito.anyLong(),
                Mockito.anyInt()
        );

        verify(
                anthropicClient,
                never()
        ).generateReply(
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.anyList()
        );
    }
}