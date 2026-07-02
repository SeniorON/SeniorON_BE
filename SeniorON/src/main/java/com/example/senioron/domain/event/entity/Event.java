package com.example.senioron.domain.event.entity;

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
public class Event extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long event_id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "users_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "triggered_users_id")
    private User triggeredUser;

    @Enumerated(EnumType.STRING)
    private EventType eventType;

    @Column(precision = 10, scale = 7)
    private java.math.BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private java.math.BigDecimal longitude;}
