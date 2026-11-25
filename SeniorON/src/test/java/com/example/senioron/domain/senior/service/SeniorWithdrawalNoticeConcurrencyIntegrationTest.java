package com.example.senioron.domain.senior.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.senioron.domain.senior.entity.SeniorRelation;
import com.example.senioron.domain.senior.entity.SeniorWithdrawalNotice;
import com.example.senioron.domain.senior.entity.SeniorWithdrawalNoticeRecipient;
import com.example.senioron.domain.senior.repository.SeniorWithdrawalNoticeRecipientRepository;
import com.example.senioron.domain.senior.repository.SeniorWithdrawalNoticeRepository;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.entity.UserStatus;
import com.example.senioron.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
        "cloud.aws.region=ap-northeast-2",
        "cloud.aws.s3.bucket=test-bucket"
})
class SeniorWithdrawalNoticeConcurrencyIntegrationTest {

    @Autowired private SeniorWithdrawalNoticeService noticeService;
    @Autowired private SeniorWithdrawalNoticeRepository noticeRepository;
    @Autowired private SeniorWithdrawalNoticeRecipientRepository recipientRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    private User firstChild;
    private User secondChild;
    private Long noticeId;

    @BeforeEach
    void setUp() {
        transactionTemplate.executeWithoutResult(status -> {
            String suffix = Long.toString(System.nanoTime());
            firstChild = userRepository.save(user("concurrent-first-" + suffix));
            secondChild = userRepository.save(user("concurrent-second-" + suffix));
            SeniorWithdrawalNotice notice = noticeRepository.save(
                    SeniorWithdrawalNotice.builder()
                            .seniorId(System.nanoTime())
                            .familyId(System.nanoTime())
                            .seniorName("동시 확인 시니어")
                            .withdrawnAt(LocalDateTime.now().minusMinutes(1))
                            .expiresAt(LocalDateTime.now().plusDays(30))
                            .build()
            );
            noticeId = notice.getSeniorWithdrawalNoticeId();
            recipientRepository.saveAll(List.of(
                    recipient(notice, firstChild),
                    recipient(notice, secondChild)
            ));
        });
    }

    @AfterEach
    void tearDown() {
        transactionTemplate.executeWithoutResult(status -> {
            if (noticeId != null) {
                recipientRepository.deleteAllByNoticeId(noticeId);
                noticeRepository.findById(noticeId).ifPresent(noticeRepository::delete);
            }
            if (firstChild != null) {
                userRepository.deleteById(firstChild.getUsersId());
            }
            if (secondChild != null) {
                userRepository.deleteById(secondChild.getUsersId());
            }
        });
    }

    @Test
    void concurrentAcknowledgementsCompleteNoticeExactlyAfterBothRecipients() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> first = executor.submit(
                    () -> acknowledgeAfterSignal(firstChild, ready, start));
            Future<?> second = executor.submit(
                    () -> acknowledgeAfterSignal(secondChild, ready, start));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        transactionTemplate.executeWithoutResult(status -> {
            SeniorWithdrawalNotice notice = noticeRepository.findById(noticeId).orElseThrow();
            List<SeniorWithdrawalNoticeRecipient> recipients =
                    recipientRepository.findAll();
            List<SeniorWithdrawalNoticeRecipient> noticeRecipients = recipients.stream()
                    .filter(recipient -> recipient.getNotice()
                            .getSeniorWithdrawalNoticeId().equals(noticeId))
                    .toList();

            assertThat(noticeRecipients).hasSize(2);
            assertThat(noticeRecipients)
                    .allSatisfy(recipient ->
                            assertThat(recipient.getAcknowledgedAt()).isNotNull());
            assertThat(notice.getCompletedAt()).isNotNull();
        });
    }

    private void acknowledgeAfterSignal(
            User child,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        ready.countDown();
        try {
            start.await();
            noticeService.acknowledgeNotice(child, noticeId);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private User user(String loginId) {
        return User.builder()
                .loginId(loginId)
                .email(loginId + "@example.com")
                .password("encoded")
                .name(loginId)
                .role(Role.CHILD)
                .status(UserStatus.ACTIVE)
                .build();
    }

    private SeniorWithdrawalNoticeRecipient recipient(
            SeniorWithdrawalNotice notice,
            User child
    ) {
        return SeniorWithdrawalNoticeRecipient.builder()
                .notice(notice)
                .recipientUser(child)
                .relation(SeniorRelation.MOTHER)
                .build();
    }
}
