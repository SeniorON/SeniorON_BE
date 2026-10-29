package com.example.senioron.domain.admin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(
        name = "admin_activity_log",
        indexes = {
                @Index(name = "idx_admin_activity_log_created_at", columnList = "created_at"),
                @Index(name = "idx_admin_activity_log_admin_created_at", columnList = "admin_id, created_at")
        }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AdminActivityLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id", nullable = false)
    private Admin admin;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, length = 50)
    private AdminActivityType activityType;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 30)
    private AdminActivityTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;
    @Column(name = "target_name", length = 100)
    private String targetName;

    @Column(nullable = false, length = 255)
    private String message;

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
