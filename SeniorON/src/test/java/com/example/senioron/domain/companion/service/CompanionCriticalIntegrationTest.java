package com.example.senioron.domain.companion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.senioron.domain.companion.dto.response.CompanionConversationStartResponse;
import com.example.senioron.domain.companion.entity.CompanionConversation;
import com.example.senioron.domain.companion.entity.CompanionTurn;
import com.example.senioron.domain.companion.entity.ConversationStatus;
import com.example.senioron.domain.companion.entity.FailureStage;
import com.example.senioron.domain.companion.entity.SafetyType;
import com.example.senioron.domain.companion.entity.TurnStatus;
import com.example.senioron.domain.companion.repository.CompanionConversationRepository;
import com.example.senioron.domain.companion.repository.CompanionTurnRepository;
import com.example.senioron.domain.companion.service.model.TurnClaimResult;
import com.example.senioron.domain.companion.service.model.TurnClaimStatus;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.entity.UserStatus;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
        "cloud.aws.region=ap-northeast-2",
        "cloud.aws.s3.bucket=test-bucket"
})
class CompanionCriticalIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanionConversationRepository
            conversationRepository;

    @Autowired
    private CompanionTurnRepository turnRepository;

    @Autowired
    private CompanionTurnClaimTransactionService
            turnClaimTransactionService;

    @Autowired
    private CompanionConversationService
            conversationService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void sameRequestIdIsClaimedOnlyOnce() {
        Long conversationId =
                createCommittedConversation();

        Long userId =
                transactionTemplate.execute(status ->
                        conversationRepository
                                .findById(conversationId)
                                .orElseThrow()
                                .getUser()
                                .getUsersId()
                );

        String requestId =
                UUID.randomUUID()
                        .toString();

        TurnClaimResult first =
                turnClaimTransactionService.claim(
                        conversationId,
                        userId,
                        requestId
                );

        TurnClaimResult second =
                turnClaimTransactionService.claim(
                        conversationId,
                        userId,
                        requestId
                );

        assertThat(first.status())
                .isEqualTo(
                        TurnClaimStatus.CREATED
                );

        assertThat(second.status())
                .isEqualTo(
                        TurnClaimStatus.PROCESSING
                );

        assertThat(second.turnId())
                .isEqualTo(first.turnId());

        assertThat(
                turnRepository
                        .findByConversationConversationIdAndRequestId(
                                conversationId,
                                requestId
                        )
        ).isPresent();
    }

    @Test
    void differentRequestIsRejectedWhileTurnIsProcessing() {
        Long conversationId =
                createCommittedConversation();

        Long userId =
                loadConversationUserId(
                        conversationId
                );

        turnClaimTransactionService.claim(
                conversationId,
                userId,
                UUID.randomUUID().toString()
        );

        assertThatThrownBy(() ->
                turnClaimTransactionService.claim(
                        conversationId,
                        userId,
                        UUID.randomUUID().toString()
                )
        ).isInstanceOfSatisfying(
                BusinessException.class,
                exception ->
                        assertThat(exception.getCode())
                                .isEqualTo(
                                        ErrorCode
                                                .COMPANION_TURN_IN_PROGRESS
                                )
        );
    }

    @Test
    void failedTurnReturnsRetryWithOriginalStage() {
        Long conversationId =
                createCommittedConversation();

        Long userId =
                loadConversationUserId(
                        conversationId
                );

        String requestId =
                UUID.randomUUID().toString();

        TurnClaimResult first =
                turnClaimTransactionService.claim(
                        conversationId,
                        userId,
                        requestId
                );

        transactionTemplate.executeWithoutResult(status -> {
            CompanionTurn turn =
                    turnRepository
                            .findById(first.turnId())
                            .orElseThrow();

            turn.markFailed(
                    FailureStage.STT
            );
        });

        TurnClaimResult retry =
                turnClaimTransactionService.claim(
                        conversationId,
                        userId,
                        requestId
                );

        assertThat(retry.status())
                .isEqualTo(TurnClaimStatus.RETRY);

        assertThat(retry.retryStage())
                .isEqualTo(FailureStage.STT);

        assertThat(retry.turnStatus())
                .isEqualTo(TurnStatus.RECEIVED);
    }

    @Test
    void completedTurnReturnsCompletedClaim() {
        Long conversationId =
                createCommittedConversation();

        Long userId =
                loadConversationUserId(
                        conversationId
                );

        String requestId =
                UUID.randomUUID().toString();

        TurnClaimResult first =
                turnClaimTransactionService.claim(
                        conversationId,
                        userId,
                        requestId
                );

        transactionTemplate.executeWithoutResult(status -> {
            CompanionTurn turn =
                    turnRepository
                            .findById(first.turnId())
                            .orElseThrow();

            turn.markTranscribed(
                    "OPENAI",
                    "gpt-4o-mini-transcribe"
            );

            turn.markSafetyResult(
                    SafetyType.NORMAL,
                    null
            );

            turn.markResponseGenerated(
                    "ANTHROPIC",
                    "claude-haiku",
                    "companion-v1",
                    10,
                    5
            );

            turn.markCompleted(
                    "GOOGLE_CLOUD",
                    "ko-KR-Neural2-A"
            );
        });

        TurnClaimResult completed =
                turnClaimTransactionService.claim(
                        conversationId,
                        userId,
                        requestId
                );

        assertThat(completed.status())
                .isEqualTo(
                        TurnClaimStatus.COMPLETED
                );

        assertThat(completed.turnStatus())
                .isEqualTo(
                        TurnStatus.COMPLETED
                );
    }

    @Test
    void persistenceFailureCannotBeRetried() {
        Long conversationId =
                createCommittedConversation();

        Long userId =
                loadConversationUserId(
                        conversationId
                );

        String requestId =
                UUID.randomUUID().toString();

        TurnClaimResult first =
                turnClaimTransactionService.claim(
                        conversationId,
                        userId,
                        requestId
                );

        transactionTemplate.executeWithoutResult(status -> {
            CompanionTurn turn =
                    turnRepository
                            .findById(first.turnId())
                            .orElseThrow();

            turn.markFailed(
                    FailureStage.PERSISTENCE
            );
        });

        assertThatThrownBy(() ->
                turnClaimTransactionService.claim(
                        conversationId,
                        userId,
                        requestId
                )
        ).isInstanceOfSatisfying(
                BusinessException.class,
                exception ->
                        assertThat(exception.getCode())
                                .isEqualTo(
                                        ErrorCode
                                                .COMPANION_PERSISTENCE_FAILED
                                )
        );
    }

    @Test
    void concurrentStartsCreateOnlyOneActiveConversation()
            throws Exception {

        User principal =
                createCommittedParent();

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        CountDownLatch readyLatch =
                new CountDownLatch(2);

        CountDownLatch startLatch =
                new CountDownLatch(1);

        Callable<CompanionConversationStartResponse> task =
                () -> {
                    readyLatch.countDown();

                    if (!startLatch.await(
                            5,
                            TimeUnit.SECONDS
                    )) {
                        throw new IllegalStateException(
                                "동시 실행 대기 시간 초과"
                        );
                    }

                    return conversationService.start(
                            principal
                    );
                };

        try {
            Future<CompanionConversationStartResponse>
                    firstFuture =
                    executor.submit(task);

            Future<CompanionConversationStartResponse>
                    secondFuture =
                    executor.submit(task);

            assertThat(
                    readyLatch.await(
                            5,
                            TimeUnit.SECONDS
                    )
            ).isTrue();

            startLatch.countDown();

            CompanionConversationStartResponse first =
                    firstFuture.get(
                            10,
                            TimeUnit.SECONDS
                    );

            CompanionConversationStartResponse second =
                    secondFuture.get(
                            10,
                            TimeUnit.SECONDS
                    );

            assertThat(first.getConversationId())
                    .isEqualTo(
                            second.getConversationId()
                    );

            assertThat(
                    List.of(
                            first.isCreated(),
                            second.isCreated()
                    )
            ).containsExactlyInAnyOrder(
                    true,
                    false
            );

            User storedParent =
                    userRepository.findById(
                            principal.getUsersId()
                    ).orElseThrow();

            assertThat(
                    conversationRepository
                            .countByUserAndStatus(
                                    storedParent,
                                    ConversationStatus.ACTIVE
                            )
            ).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private Long createCommittedConversation() {
        return transactionTemplate.execute(status -> {
            User parent =
                    createParentEntity(
                            "turn-transaction"
                    );

            userRepository.save(parent);

            CompanionConversation conversation =
                    conversationRepository.save(
                            CompanionConversation.start(
                                    parent
                            )
                    );

            return conversation
                    .getConversationId();
        });
    }

    private Long loadConversationUserId(
            Long conversationId
    ) {
        return transactionTemplate.execute(status ->
                conversationRepository
                        .findById(conversationId)
                        .orElseThrow()
                        .getUser()
                        .getUsersId()
        );
    }

    private User createCommittedParent() {
        return transactionTemplate.execute(status ->
                userRepository.save(
                        createParentEntity(
                                "concurrency"
                        )
                )
        );
    }

    private User createParentEntity(
            String prefix
    ) {
        String unique =
                UUID.randomUUID().toString();

        return User.builder()
                .loginId(
                        prefix + "-" + unique
                )
                .email(
                        prefix
                                + "-"
                                + unique
                                + "@test.com"
                )
                .password(
                        "encoded-password"
                )
                .name("테스트 부모")
                .role(Role.PARENT)
                .status(UserStatus.ACTIVE)
                .build();
    }
}
