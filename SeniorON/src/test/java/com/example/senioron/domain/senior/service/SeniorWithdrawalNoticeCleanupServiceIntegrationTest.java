package com.example.senioron.domain.senior.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.senioron.domain.notification.entity.Notification;
import com.example.senioron.domain.notification.entity.NotificationType;
import com.example.senioron.domain.notification.repository.NotificationRepository;
import com.example.senioron.domain.senior.entity.SeniorRelation;
import com.example.senioron.domain.senior.entity.SeniorWithdrawalNotice;
import com.example.senioron.domain.senior.entity.SeniorWithdrawalNoticeRecipient;
import com.example.senioron.domain.senior.repository.SeniorWithdrawalNoticeRecipientRepository;
import com.example.senioron.domain.senior.repository.SeniorWithdrawalNoticeRepository;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.entity.UserStatus;
import com.example.senioron.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "cloud.aws.region=ap-northeast-2",
        "cloud.aws.s3.bucket=test-bucket",
        "senior.withdrawal-notice.completion-grace-days=1"
})
@Transactional
class SeniorWithdrawalNoticeCleanupServiceIntegrationTest {

    @Autowired private SeniorWithdrawalNoticeCleanupService cleanupService;
    @Autowired private SeniorWithdrawalNoticeRepository noticeRepository;
    @Autowired private SeniorWithdrawalNoticeRecipientRepository recipientRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void deletesExpiredAndGraceElapsedNoticesOnlyAndCanRunRepeatedly() {
        LocalDateTime now = LocalDateTime.now();
        User child = userRepository.save(User.builder()
                .loginId("withdrawal-cleanup-child")
                .email("withdrawal-cleanup-child@example.com")
                .password("encoded")
                .name("정리 테스트 자녀")
                .role(Role.CHILD)
                .status(UserStatus.ACTIVE)
                .build());
        Notification generalNotification = notificationRepository.save(
                Notification.builder()
                        .notificationType(NotificationType.SOS)
                        .body("일반 알림은 유지")
                        .isRead(false)
                        .build()
        );

        SeniorWithdrawalNotice expired = notice(
                401L, now.minusDays(31), now.minusMinutes(1), null);
        SeniorWithdrawalNoticeRecipient expiredRecipient =
                recipient(expired, child, null);

        LocalDateTime oldCompletion = now.minusDays(2);
        SeniorWithdrawalNotice graceElapsed = notice(
                402L, now.minusDays(2), now.plusDays(28), oldCompletion);
        SeniorWithdrawalNoticeRecipient graceElapsedRecipient =
                recipient(graceElapsed, child, oldCompletion);

        SeniorWithdrawalNotice active = notice(
                403L, now.minusHours(1), now.plusDays(30), null);
        SeniorWithdrawalNoticeRecipient activeRecipient =
                recipient(active, child, null);

        LocalDateTime recentCompletion = now.minusHours(12);
        SeniorWithdrawalNotice withinGrace = notice(
                404L, now.minusHours(12), now.plusDays(29), recentCompletion);
        SeniorWithdrawalNoticeRecipient withinGraceRecipient =
                recipient(withinGrace, child, recentCompletion);

        entityManager.flush();
        entityManager.clear();

        assertThat(cleanupService.cleanupEligibleNotices()).isEqualTo(2);
        entityManager.flush();
        entityManager.clear();

        assertThat(noticeRepository.findById(
                expired.getSeniorWithdrawalNoticeId())).isEmpty();
        assertThat(noticeRepository.findById(
                graceElapsed.getSeniorWithdrawalNoticeId())).isEmpty();
        assertThat(recipientRepository.findById(
                expiredRecipient.getSeniorWithdrawalNoticeRecipientId())).isEmpty();
        assertThat(recipientRepository.findById(
                graceElapsedRecipient.getSeniorWithdrawalNoticeRecipientId())).isEmpty();

        assertThat(noticeRepository.findById(
                active.getSeniorWithdrawalNoticeId())).isPresent();
        assertThat(noticeRepository.findById(
                withinGrace.getSeniorWithdrawalNoticeId())).isPresent();
        assertThat(recipientRepository.findById(
                activeRecipient.getSeniorWithdrawalNoticeRecipientId())).isPresent();
        assertThat(recipientRepository.findById(
                withinGraceRecipient.getSeniorWithdrawalNoticeRecipientId())).isPresent();
        assertThat(userRepository.findById(child.getUsersId())).isPresent();
        assertThat(notificationRepository.findById(
                generalNotification.getNotificationId())).isPresent();

        assertThat(cleanupService.cleanupEligibleNotices()).isZero();
    }

    private SeniorWithdrawalNotice notice(
            Long seniorId,
            LocalDateTime withdrawnAt,
            LocalDateTime expiresAt,
            LocalDateTime completedAt
    ) {
        SeniorWithdrawalNotice notice = SeniorWithdrawalNotice.builder()
                .seniorId(seniorId)
                .familyId(seniorId)
                .seniorName("정리 대상 " + seniorId)
                .withdrawnAt(withdrawnAt)
                .expiresAt(expiresAt)
                .build();
        if (completedAt != null) {
            notice.complete(completedAt);
        }
        return noticeRepository.save(notice);
    }

    private SeniorWithdrawalNoticeRecipient recipient(
            SeniorWithdrawalNotice notice,
            User child,
            LocalDateTime acknowledgedAt
    ) {
        SeniorWithdrawalNoticeRecipient recipient =
                SeniorWithdrawalNoticeRecipient.builder()
                        .notice(notice)
                        .recipientUser(child)
                        .relation(SeniorRelation.MOTHER)
                        .build();
        if (acknowledgedAt != null) {
            recipient.acknowledge(acknowledgedAt);
        }
        return recipientRepository.save(recipient);
    }
}
