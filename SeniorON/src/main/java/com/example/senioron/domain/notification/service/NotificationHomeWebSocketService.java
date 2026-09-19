package com.example.senioron.domain.notification.service;

import com.example.senioron.domain.notification.dto.response.NotificationHomeUpdate;
import com.example.senioron.domain.notification.entity.Notification;
import com.example.senioron.domain.notification.repository.NotificationRepository;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.UserStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationHomeWebSocketService {
    public static final String DESTINATION = "/queue/notification-home";

    private final ApplicationEventPublisher events;
    private final SimpMessagingTemplate messaging;
    private final NotificationRepository notifications;

    public void notifyCreated(List<Notification> savedNotifications) {
        savedNotifications.forEach(notification -> publish(notification, "CREATED"));
    }

    // SOS 주소 UPDATE의 별도 트랜잭션이 완료된 뒤 호출된다.
    @Transactional(readOnly = true)
    public void notifyAddressUpdated(Long eventId) {
        notifications.findHomeUpdateRecipients(eventId, Role.CHILD, UserStatus.ACTIVE)
                .forEach(notification -> publish(notification, "ADDRESS_UPDATED"));
    }

    private void publish(Notification notification, String reason) {
        var event = notification.getEvent();
        // 커밋 이후 lazy 엔티티를 읽지 않도록 트랜잭션 안에서 값만 복사한다.
        var payload = new NotificationHomeUpdate("NOTIFICATION_HOME_UPDATED",
                event.getSenior().getSeniorId(), notification.getNotificationId(),
                event.getEventId(), notification.getNotificationType(), reason);
        events.publishEvent(new Delivery(notification.getReceiverUser().getUsersId(), payload));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendAfterCommit(Delivery delivery) {
        try {
            messaging.convertAndSendToUser(delivery.receiverId().toString(), DESTINATION, delivery.payload());
        } catch (RuntimeException exception) {
            // 실시간 갱신 실패가 이미 저장된 알림 또는 이후 FCM 발송을 방해하지 않는다.
            log.warn("알림 메인 갱신 신호 전송 실패. receiverId={}, eventId={}",
                    delivery.receiverId(), delivery.payload().eventId(), exception);
        }
    }

    public record Delivery(Long receiverId, NotificationHomeUpdate payload) {
    }
}
