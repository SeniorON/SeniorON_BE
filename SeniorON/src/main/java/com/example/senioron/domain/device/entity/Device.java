package com.example.senioron.domain.device.entity;

import com.example.senioron.common.entity.BaseEntity;
import com.example.senioron.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        uniqueConstraints = @UniqueConstraint(columnNames = {"device_identifier"}),
        indexes = {
                @Index(name = "idx_device_users_id", columnList = "users_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Device extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "device_id")
    private Long deviceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "users_id")
    private User user;

    private String deviceIdentifier;

    private String deviceToken;

    private String deviceName;

    @Enumerated(EnumType.STRING)
    private DeviceStatus connectionStatus;

    private Integer batteryLevel;

    private LocalDateTime lastConnectedAt;

    public void updateDeviceStatus(
            DeviceStatus connectionStatus,
            Integer batteryLevel,
            LocalDateTime lastConnectedAt
    ) {
        this.connectionStatus = connectionStatus;
        this.batteryLevel = batteryLevel;
        this.lastConnectedAt = lastConnectedAt;
    }

    public void updateDeviceToken(String deviceToken) {
        this.deviceToken = deviceToken;
    }

    // 같은 기기(device_identifier)에 다른 계정이 로그인하면 소유자를 그 계정으로 교체한다.
    public void reassignOwner(User user) {
        this.user = user;
    }

    public void updateDeviceInfo(
            String deviceName,
            DeviceStatus connectionStatus,
            Integer batteryLevel,
            LocalDateTime lastConnectedAt
    ) {
        this.deviceName = deviceName;
        this.connectionStatus = connectionStatus;
        this.batteryLevel = batteryLevel;
        this.lastConnectedAt = lastConnectedAt;
    }

    public void disconnect() {
        this.connectionStatus = DeviceStatus.DISCONNECTED;
    }
}