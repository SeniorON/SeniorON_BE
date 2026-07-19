package com.example.senioron.domain.notification.entity;

import com.example.senioron.domain.event.entity.Event;
import com.example.senioron.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.*;
import lombok.*;
import com.example.senioron.common.entity.BaseEntity;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Notification extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long notificationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "send_users_id")
    private User sendUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_users_id")
    private User receiverUser;

    @Enumerated(EnumType.STRING)
    private NotificationType notificationType;

    private String body;

    private String title;

    private String linkUrl;

    private Boolean isRead;

    private LocalDateTime readAt;}
