package com.example.senioron.domain.companion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.senioron.domain.companion.dto.response.CompanionConversationStartResponse;
import com.example.senioron.domain.companion.entity.CompanionConversation;
import com.example.senioron.domain.companion.entity.ConversationStatus;
import com.example.senioron.domain.companion.repository.CompanionConversationRepository;
import com.example.senioron.domain.companion.repository.CompanionTurnRepository;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.entity.UserStatus;
import com.example.senioron.domain.user.repository.UserRepository;
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
    private CompanionTurnCreationTransactionService
            turnCreationTransactionService;

    @Autowired
    private CompanionConversationService
            conversationService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void existingTurnCanBeQueriedAfterDuplicateTransactionRollsBack() {
        Long conversationId =
                createCommittedConversation();

        String requestId =
                UUID.randomUUID().toString();

        Long createdTurnId =
                turnCreationTransactionService.create(
                        conversationId,
                        requestId
                );

        assertThat(createdTurnId)
                .isNotNull();

        assertThatThrownBy(() ->
                turnCreationTransactionService.create(
                        conversationId,
                        requestId
                )
        ).isInstanceOf(
                DataIntegrityViolationException.class
        );

        assertThat(
                turnRepository
                        .findByConversationConversationIdAndRequestId(
                                conversationId,
                                requestId
                        )
        ).isPresent();
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