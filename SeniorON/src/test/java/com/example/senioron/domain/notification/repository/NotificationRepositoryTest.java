package com.example.senioron.domain.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.senioron.domain.notification.entity.Notification;
import com.example.senioron.domain.notification.entity.NotificationType;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "cloud.aws.region=ap-northeast-2",
        "cloud.aws.s3.bucket=test-bucket"
})
@Transactional
class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void deleteAllByCreatedAtBeforeRemovesOnlyNotificationsOlderThanThreshold() {
        Notification oldNotification = notificationRepository.save(
                Notification.builder().notificationType(NotificationType.SOS).body("old").isRead(false).build());
        Notification recentNotification = notificationRepository.save(
                Notification.builder().notificationType(NotificationType.SOS).body("recent").isRead(false).build());
        entityManager.flush();

        backdate(oldNotification.getNotificationId(), LocalDateTime.now().minusDays(40));
        backdate(recentNotification.getNotificationId(), LocalDateTime.now().minusDays(10));
        entityManager.clear();

        int deletedCount = notificationRepository.deleteAllByCreatedAtBefore(LocalDateTime.now().minusDays(30));

        assertThat(deletedCount).isEqualTo(1);
        assertThat(notificationRepository.findById(oldNotification.getNotificationId())).isEmpty();
        assertThat(notificationRepository.findById(recentNotification.getNotificationId())).isPresent();
    }

    private void backdate(Long notificationId, LocalDateTime createdAt) {
        entityManager.createNativeQuery("UPDATE notification SET created_at = ?1 WHERE notification_id = ?2")
                .setParameter(1, createdAt)
                .setParameter(2, notificationId)
                .executeUpdate();
    }
}
