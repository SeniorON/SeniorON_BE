package com.example.senioron.domain.permission.entity;

import com.example.senioron.common.entity.BaseEntity;
import com.example.senioron.domain.senior.entity.Senior;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "senior_permission_setting")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SeniorPermissionSetting extends BaseEntity {

    @Id
    @Column(name = "senior_id")
    private Long seniorId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "senior_id", nullable = false)
    private Senior senior;

    @Builder.Default
    @Column(name = "location_enabled", nullable = false)
    private Boolean locationEnabled = true;

    @Builder.Default
    @Column(name = "inactivity_detection_enabled", nullable = false)
    private Boolean inactivityDetectionEnabled = true;

    public void update(Boolean locationEnabled, Boolean inactivityDetectionEnabled) {
        if (locationEnabled != null) {
            this.locationEnabled = locationEnabled;
        }
        if (inactivityDetectionEnabled != null) {
            this.inactivityDetectionEnabled = inactivityDetectionEnabled;
        }
    }
}
