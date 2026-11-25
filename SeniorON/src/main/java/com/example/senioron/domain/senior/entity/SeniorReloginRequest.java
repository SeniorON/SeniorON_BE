package com.example.senioron.domain.senior.entity;

import com.example.senioron.common.entity.BaseEntity;
import com.example.senioron.domain.device.entity.Device;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "senior_relogin_requests",
        indexes = {
                @Index(name = "idx_senior_relogin_requests_senior_status", columnList = "senior_id, status"),
                @Index(name = "idx_senior_relogin_requests_device_status", columnList = "device_id, status"),
                @Index(name = "idx_senior_relogin_requests_expires_at", columnList = "expires_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SeniorReloginRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "senior_relogin_request_id")
    private Long seniorReloginRequestId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "senior_id", nullable = false)
    private Senior senior;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeniorReloginRequestStatus status;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    public boolean isExpired(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isPendingAndValid(LocalDateTime now) {
        return status == SeniorReloginRequestStatus.PENDING && !isExpired(now);
    }

    public void approve(LocalDateTime expiresAt) {
        this.status = SeniorReloginRequestStatus.APPROVED;
        this.expiresAt = expiresAt;
    }

    public void expire() {
        this.status = SeniorReloginRequestStatus.EXPIRED;
    }

    public void use() {
        this.status = SeniorReloginRequestStatus.USED;
    }
}
