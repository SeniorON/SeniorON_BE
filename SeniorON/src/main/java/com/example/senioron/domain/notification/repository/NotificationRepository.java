package com.example.senioron.domain.notification.repository;

import com.example.senioron.domain.notification.entity.Notification;
import com.example.senioron.domain.notification.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    @Query("""
        SELECT n FROM Notification n
        WHERE n.receiverUser.usersId = :userId
        AND n.notificationType = :type
        AND n.isRead = false
        ORDER BY n.createdAt DESC
        LIMIT 1
        """)
    // 알림 메인화면 표시 알람 (읽지 않은 최신 1건의 알림만)
    Optional<Notification> findLatestUnread(
            @Param("userId") Long userId,
            @Param("type") NotificationType type
    );
}
