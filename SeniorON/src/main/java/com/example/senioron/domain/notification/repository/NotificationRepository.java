package com.example.senioron.domain.notification.repository;

import com.example.senioron.domain.notification.entity.Notification;
import com.example.senioron.domain.notification.entity.NotificationType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    // 홈 화면 카드용. 타입별로 따로 조회하지 않고 한 번에 가져와, 자바에서 타입별 최신 1건만 뽑는다.
    // sendUser/event는 카드에 바로 필요해서 지연 로딩 대신 같이 가져온다.
    @Query("""
    SELECT n FROM Notification n
    LEFT JOIN FETCH n.sendUser
    LEFT JOIN FETCH n.event
    WHERE n.receiverUser.usersId = :userId
    AND n.notificationType IN :types
    AND n.isRead = false
    AND n.createdAt >= :threshold
    ORDER BY n.createdAt DESC
    """)
    List<Notification> findLatestUnreadByTypes(
            @Param("userId") Long userId,
            @Param("types") List<NotificationType> types,
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

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.createdAt < :threshold")
    int deleteAllByCreatedAtBefore(@Param("threshold") LocalDateTime threshold);

}
