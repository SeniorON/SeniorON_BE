package com.example.senioron.domain.notification.repository;

import com.example.senioron.domain.notification.entity.Notification;
import com.example.senioron.domain.notification.entity.NotificationType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    @Query("""
    SELECT n FROM Notification n
    WHERE n.receiverUser.usersId = :userId
    AND n.notificationType = :type
    AND n.isRead = false
    AND n.createdAt >= :threshold
    ORDER BY n.createdAt DESC
    LIMIT 1
    """)
    Optional<Notification> findLatestUnread(
            @Param("userId") Long userId,
            @Param("type") NotificationType type,
            @Param("threshold") LocalDateTime threshold
    );

    @Query("""
    SELECT n FROM Notification n
    WHERE n.receiverUser.usersId = :userId
    AND n.notificationType = :type
    AND n.createdAt >= :thirtyDaysAgo
    AND (:cursor IS NULL OR n.notificationId < :cursor)
    ORDER BY n.notificationId DESC
    """)
    List<Notification> findByTypeWithCursor(
            @Param("userId") Long userId,
            @Param("type") NotificationType type,
            @Param("thirtyDaysAgo") LocalDateTime thirtyDaysAgo,
            @Param("cursor") Long cursor,
            Pageable pageable
    );

    @Query("""
    SELECT COUNT(n) FROM Notification n
    WHERE n.receiverUser.usersId = :userId
    AND n.notificationType = :type
    AND n.createdAt >= :thirtyDaysAgo
    """)
    long countByTypeWithin30Days(
            @Param("userId") Long userId,
            @Param("type") NotificationType type,
            @Param("thirtyDaysAgo") LocalDateTime thirtyDaysAgo
    );

}
