package com.example.senioron.domain.inactivity.entity;

import com.example.senioron.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.*;
import lombok.*;
import com.example.senioron.common.entity.BaseEntity;

@Entity
@Table(name = "inactivity_setting")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class InactivitySetting extends BaseEntity {

    public static final int DEFAULT_THRESHOLD_HOURS = 4;

    @Id
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Builder.Default
    @Column(nullable = false)
    private Integer thresholdHours = DEFAULT_THRESHOLD_HOURS;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isEnabled = true;

    public void updateThresholdHours(Integer thresholdHours) {
        this.thresholdHours = thresholdHours;
    }
}
