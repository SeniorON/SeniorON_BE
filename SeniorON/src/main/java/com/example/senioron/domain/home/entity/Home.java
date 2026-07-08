package com.example.senioron.domain.home.entity;

import com.example.senioron.common.entity.BaseEntity;
import com.example.senioron.domain.device.entity.Device;
import com.example.senioron.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Home extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long homeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "users_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;

    private Integer buttonOrder;

    private String buttonName;

    private String icon;

    @Enumerated(EnumType.STRING)
    private ActionType actionType;

    private String actionValue;

    @Enumerated(EnumType.STRING)
    private FontSize fontSize;
}