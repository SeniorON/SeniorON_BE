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

    @Id
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Builder.Default
    @Column(nullable = false)
    private Integer thresholdHours = 24;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isEnabled = true;

    public void updateThresholdHours(Integer thresholdHours) {
        this.thresholdHours = thresholdHours;
    }

    public void updateIsEnabled(Boolean isEnabled) {
        this.isEnabled = isEnabled;
    }
}
