package com.example.senioron.domain.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.senioron.domain.event.entity.Event;
import com.example.senioron.domain.event.entity.EventType;
import com.example.senioron.domain.event.repository.EventRepository;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.repository.FamilyRepository;
import com.example.senioron.domain.notification.entity.Notification;
import com.example.senioron.domain.notification.entity.NotificationType;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.repository.SeniorRepository;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
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

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FamilyRepository familyRepository;

    @Autowired
    private SeniorRepository seniorRepository;

    @Autowired
    private EventRepository eventRepository;

    @Test
    void deleteAllByCreatedAtBeforeRemovesOnlyNotificationsOlderThanThreshold() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(30).truncatedTo(ChronoUnit.MILLIS);

        Notification oldNotification = save("old");
        Notification boundaryNotification = save("boundary");
        Notification recentNotification = save("recent");
        entityManager.flush();

        backdate(oldNotification.getNotificationId(), threshold.minusDays(10));
        backdate(boundaryNotification.getNotificationId(), threshold);
        backdate(recentNotification.getNotificationId(), threshold.plusDays(20));
        entityManager.clear();

        int deletedCount = notificationRepository.deleteAllByCreatedAtBefore(threshold);

        assertThat(deletedCount).isEqualTo(1);
        assertThat(notificationRepository.findById(oldNotification.getNotificationId())).isEmpty();
        assertThat(notificationRepository.findById(boundaryNotification.getNotificationId())).isPresent();
        assertThat(notificationRepository.findById(recentNotification.getNotificationId())).isPresent();
    }

    @Test
    void notificationQueriesSeparateSameReceiversNotificationsBySeniorId() {
        User receiver = saveUser("notification-receiver", Role.CHILD);
        User parentA = saveUser("notification-parent-a", Role.PARENT);
        User parentB = saveUser("notification-parent-b", Role.PARENT);
        Family familyA = saveFamily("NOTIFICATION-A");
        Family familyB = saveFamily("NOTIFICATION-B");
        Senior seniorA = saveSenior("시니어A", familyA, receiver, parentA, "01011111111");
        Senior seniorB = saveSenior("시니어B", familyB, receiver, parentB, "01022222222");
        Event eventA = eventRepository.save(Event.builder()
                .user(parentA)
                .triggeredUser(parentA)
                .senior(seniorA)
                .eventType(EventType.SOS)
                .build());
        Event eventB = eventRepository.save(Event.builder()
                .user(parentB)
                .triggeredUser(parentB)
                .senior(seniorB)
                .eventType(EventType.SOS)
                .build());
        Notification notificationA = save(receiver, parentA, eventA, "senior-a");
        Notification notificationB = save(receiver, parentB, eventB, "senior-b");
        entityManager.flush();
        entityManager.clear();

        LocalDateTime threshold = LocalDateTime.now().minusDays(30);
        List<Notification> homeItems = notificationRepository.findLatestUnreadByTypes(
                receiver.getUsersId(), seniorA.getSeniorId(), List.of(NotificationType.SOS), threshold);
        List<Notification> listItems = notificationRepository.findByTypeWithCursor(
                receiver.getUsersId(), seniorA.getSeniorId(), NotificationType.SOS,
                threshold, null, PageRequest.of(0, 20));
        long count = notificationRepository.countByTypeWithin30Days(
                receiver.getUsersId(), seniorA.getSeniorId(), NotificationType.SOS, threshold);

        assertThat(homeItems).extracting(Notification::getNotificationId)
                .containsExactly(notificationA.getNotificationId())
                .doesNotContain(notificationB.getNotificationId());
        assertThat(listItems).extracting(Notification::getNotificationId)
                .containsExactly(notificationA.getNotificationId())
                .doesNotContain(notificationB.getNotificationId());
        assertThat(count).isEqualTo(1);
    }

    private Notification save(String body) {
        return notificationRepository.save(
                Notification.builder().notificationType(NotificationType.SOS).body(body).isRead(false).build());
    }

    private Notification save(User receiver, User sender, Event event, String body) {
        return notificationRepository.save(Notification.builder()
                .event(event)
                .sendUser(sender)
                .receiverUser(receiver)
                .notificationType(NotificationType.SOS)
                .title("SOS 알림")
                .body(body)
                .isRead(false)
                .build());
    }

    private User saveUser(String loginId, Role role) {
        return userRepository.save(User.builder()
                .loginId(loginId + "-" + System.nanoTime())
                .name(loginId)
                .role(role)
                .build());
    }

    private Family saveFamily(String code) {
        return familyRepository.save(Family.builder()
                .seniorCode(code + "-" + System.nanoTime())
                .build());
    }

    private Senior saveSenior(
            String name,
            Family family,
            User registeredBy,
            User parent,
            String phoneNumber
    ) {
        return seniorRepository.save(Senior.builder()
                .name(name)
                .birth(LocalDate.of(1950, 1, 1))
                .phoneNumber(phoneNumber)
                .family(family)
                .registeredBy(registeredBy)
                .parentUser(parent)
                .build());
    }

    private void backdate(Long notificationId, LocalDateTime createdAt) {
        entityManager.createNativeQuery("UPDATE notification SET created_at = ?1 WHERE notification_id = ?2")
                .setParameter(1, createdAt)
                .setParameter(2, notificationId)
                .executeUpdate();
    }
}
