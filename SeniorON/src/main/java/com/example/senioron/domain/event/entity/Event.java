package com.example.senioron.domain.event.entity;

import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.senior.entity.Senior;
import jakarta.persistence.*;
import lombok.*;
import com.example.senioron.common.entity.BaseEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "event", indexes = {
        @Index(name = "idx_event_triggered_user_type", columnList = "triggered_users_id, eventType")
}, uniqueConstraints = @UniqueConstraint(name = "uk_event_user_client_event", columnNames = {"triggered_users_id", "client_event_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Event extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long eventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "users_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "triggered_users_id")
    private User triggeredUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "senior_id")
    private Senior senior;

    @Enumerated(EnumType.STRING)
    private EventType eventType;

    @Enumerated(EnumType.STRING)
    private OutingPhase phase;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "detection_type", length = 32)
    private OutingDetectionType detectionType;

    @Column(name = "client_event_id", length = 128)
    private String clientEventId;

    // BaseEntity와 동일하게 한국 현지 시각으로 저장한다.
    @Column(name = "detected_at")
    private LocalDateTime detectedAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "preparation_status", length = 32)
    private com.example.senioron.domain.notification.dto.NotificationPreparationResult.Status preparationStatus;

    @Column(name = "preparation_reason", length = 128)
    private String preparationReason;

    @Column(name = "preparation_receiver_count")
    private Integer preparationReceiverCount;

    public void recordPreparationResult(com.example.senioron.domain.notification.dto.NotificationPreparationResult result) {
        preparationStatus = result.notificationStatus();
        preparationReason = result.reason();
        preparationReceiverCount = result.receiverCount();
    }

    public OffsetDateTime getDetectedAtWithOffset() {
        return detectedAt == null ? null : detectedAt.atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime();
    }

    public LocalDateTime getOutingOccurredAt() {
        return detectedAt == null ? getCreatedAt() : detectedAt;
    }

    @Column(precision = 10, scale = 7)
    private java.math.BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private java.math.BigDecimal longitude;

    private Integer deviceBattery;

    private String address;

    private LocalDateTime lastSeenAt;

    private String linkUrl;

    private Boolean isDangerous;
}
