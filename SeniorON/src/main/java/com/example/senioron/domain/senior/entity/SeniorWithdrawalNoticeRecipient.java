package com.example.senioron.domain.senior.entity;

import com.example.senioron.common.entity.BaseEntity;
import com.example.senioron.domain.user.entity.User;
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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "senior_withdrawal_notice_recipient",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_senior_withdrawal_notice_recipient",
                columnNames = {"senior_withdrawal_notice_id", "recipient_user_id"}
        ),
        indexes = {
                @Index(
                        name = "idx_withdrawal_recipient_user_ack",
                        columnList = "recipient_user_id, acknowledged_at"
                )
        }
)
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeniorWithdrawalNoticeRecipient extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "senior_withdrawal_notice_recipient_id")
    private Long seniorWithdrawalNoticeRecipientId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "senior_withdrawal_notice_id", nullable = false)
    private SeniorWithdrawalNotice notice;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_user_id", nullable = false)
    private User recipientUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation")
    private SeniorRelation relation;

    @Column(name = "custom_relation")
    private String customRelation;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    public void acknowledge(LocalDateTime acknowledgedAt) {
        if (this.acknowledgedAt == null) {
            this.acknowledgedAt = acknowledgedAt;
        }
    }
}
