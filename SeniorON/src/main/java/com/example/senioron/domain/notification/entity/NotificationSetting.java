package com.example.senioron.domain.notification.entity;

import com.example.senioron.common.entity.BaseEntity;
import com.example.senioron.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationSetting extends BaseEntity {

    @Id
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Builder.Default
    @Column(nullable = false)
    private Boolean inactivityEnabled = true;

    @Builder.Default
    @Column(nullable = false)
    private Boolean riskLinkEnabled = true;

    @Builder.Default
    @Column(nullable = false)
    private Boolean outingReturnEnabled = true;

    public void updateInactivityEnabled(Boolean inactivityEnabled) {
        this.inactivityEnabled = inactivityEnabled;
    }
    public void updateRiskLinkEnabled(Boolean riskLinkEnabled) {
        this.riskLinkEnabled = riskLinkEnabled;
    }
    public void updateOutingReturnEnabled(Boolean outingReturnEnabled) {
        this.outingReturnEnabled = outingReturnEnabled;
    }
}