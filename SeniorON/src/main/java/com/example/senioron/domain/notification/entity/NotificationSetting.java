package com.example.senioron.domain.notification.entity;

import com.example.senioron.common.entity.BaseEntity;
import com.example.senioron.domain.family.entity.FamilyMember;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "family_member_notification_setting")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationSetting extends BaseEntity {

    @Id
    private Long familyMemberId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_member_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private FamilyMember familyMember;

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
