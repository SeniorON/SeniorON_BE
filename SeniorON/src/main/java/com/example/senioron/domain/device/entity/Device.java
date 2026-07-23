package com.example.senioron.domain.device.entity;

import com.example.senioron.common.entity.BaseEntity;
import com.example.senioron.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"users_id", "device_identifier"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Device extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long device_id;

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
}