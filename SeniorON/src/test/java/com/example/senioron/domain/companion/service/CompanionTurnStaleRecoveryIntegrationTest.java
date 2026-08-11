package com.example.senioron.domain.companion.service;

import com.example.senioron.domain.companion.entity.CompanionConversation;
import com.example.senioron.domain.companion.entity.CompanionTurn;
import com.example.senioron.domain.companion.entity.FailureStage;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "cloud.aws.region=ap-northeast-2",
        "cloud.aws.s3.bucket=test-bucket"
})
class CompanionTurnStaleRecoveryIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanionConversationRepository
            conversationRepository;

    @Autowired
    private CompanionTurnRepository
            turnRepository;

    @Autowired
    private CompanionTurnClaimTransactionService
            turnClaimTransactionService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void staleProcessingTurnIsFailedAndNewRequestIsCreated() {
        ConversationFixture fixture =
                createConversation();

        TurnClaimResult staleClaim =
                turnClaimTransactionService.claim(
                        fixture.conversationId(),
                        fixture.userId(),
                        UUID.randomUUID().toString()
                );

        makeTurnStale(
                staleClaim.turnId()
        );

        TurnClaimResult newClaim =
                turnClaimTransactionService.claim(
                        fixture.conversationId(),
                        fixture.userId(),
                        UUID.randomUUID().toString()
                );

        assertThat(newClaim.status())
                .isEqualTo(
                        TurnClaimStatus.CREATED
                );

        CompanionTurn failedTurn =
                turnRepository
                        .findById(
                                staleClaim.turnId()
                        )
                        .orElseThrow();

        assertThat(failedTurn.getStatus())
                .isEqualTo(
                        TurnStatus.FAILED
                );

        assertThat(failedTurn.getFailureStage())
                .isEqualTo(
                        FailureStage.PERSISTENCE
                );
    }

    @Test
    void recentProcessingTurnStillBlocksNewRequest() {
        ConversationFixture fixture =
                createConversation();

        turnClaimTransactionService.claim(
                fixture.conversationId(),
                fixture.userId(),
                UUID.randomUUID().toString()
        );

        assertThatThrownBy(() ->
                turnClaimTransactionService.claim(
                        fixture.conversationId(),
                        fixture.userId(),
                        UUID.randomUUID().toString()
                )
        ).isInstanceOfSatisfying(
                BusinessException.class,
                exception ->
                        assertThat(
                                exception.getCode()
                        ).isEqualTo(
                                ErrorCode
                                        .COMPANION_TURN_IN_PROGRESS
                        )
        );
    }

    @Test
    void staleSameRequestCannotBeAutomaticallyRetried() {
        ConversationFixture fixture =
                createConversation();

        String requestId =
                UUID.randomUUID().toString();

        TurnClaimResult first =
                turnClaimTransactionService.claim(
                        fixture.conversationId(),
                        fixture.userId(),
                        requestId
                );

        makeTurnStale(
                first.turnId()
        );

        assertThatThrownBy(() ->
                turnClaimTransactionService.claim(
                        fixture.conversationId(),
                        fixture.userId(),
                        requestId
                )
        ).isInstanceOfSatisfying(
                BusinessException.class,
                exception ->
                        assertThat(
                                exception.getCode()
                        ).isEqualTo(
                                ErrorCode
                                        .COMPANION_PERSISTENCE_FAILED
                        )
        );

        CompanionTurn failedTurn =
                turnRepository
                        .findById(
                                first.turnId()
                        )
                        .orElseThrow();

        assertThat(failedTurn.getStatus())
                .isEqualTo(
                        TurnStatus.FAILED
                );

        assertThat(failedTurn.getFailureStage())
                .isEqualTo(
                        FailureStage.PERSISTENCE
                );

        TurnClaimResult newRequest =
                turnClaimTransactionService.claim(
                        fixture.conversationId(),
                        fixture.userId(),
                        UUID.randomUUID().toString()
                );

        assertThat(newRequest.status())
                .isEqualTo(
                        TurnClaimStatus.CREATED
                );
    }

    private void makeTurnStale(
            Long turnId
    ) {
        // 운영 stale 기준은 5분이다.
        // 경계 시간 오차를 피하려고 1분의 여유를 둔다.
        LocalDateTime staleUpdatedAt =
                LocalDateTime.now()
                        .minusMinutes(6);

        int updatedRows =
                jdbcTemplate.update(
                        """
                        UPDATE companion_turns
                        SET updated_at = ?
                        WHERE turn_id = ?
                        """,
                        staleUpdatedAt,
                        turnId
                );

        assertThat(updatedRows)
                .isEqualTo(1);
    }

    private ConversationFixture createConversation() {
        return transactionTemplate.execute(status -> {
            User parent =
                    userRepository.save(
                            createParent()
                    );

            CompanionConversation conversation =
                    conversationRepository.save(
                            CompanionConversation.start(
                                    parent
                            )
                    );

            return new ConversationFixture(
                    conversation.getConversationId(),
                    parent.getUsersId()
            );
        });
    }

    private User createParent() {
        String unique =
                UUID.randomUUID().toString();

        return User.builder()
                .loginId(
                        "stale-turn-" + unique
                )
                .email(
                        "stale-turn-"
                                + unique
                                + "@test.com"
                )
                .password(
                        "encoded-password"
                )
                .name(
                        "테스트 부모"
                )
                .role(
                        Role.PARENT
                )
                .status(
                        UserStatus.ACTIVE
                )
                .build();
    }

    private record ConversationFixture(
            Long conversationId,
            Long userId
    ) {
    }
}
