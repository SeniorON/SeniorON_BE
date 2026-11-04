package com.example.senioron.domain.senior.entity;

import com.example.senioron.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
        name = "senior_withdrawal_notice",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_senior_withdrawal_notice_senior",
                columnNames = "senior_id"
        ),
        indexes = {
                @Index(
                        name = "idx_senior_withdrawal_notice_family",
                        columnList = "family_id"
                ),
                @Index(
                        name = "idx_senior_withdrawal_notice_expires_at",
                        columnList = "expires_at"
                )
        }
)
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeniorWithdrawalNotice extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "senior_withdrawal_notice_id")
    private Long seniorWithdrawalNoticeId;

    /*
     * 탈퇴 후 Senior/Family가 삭제되거나 재구성되어도 원래 식별자를 보존하기 위한
     * 스냅샷 값이다. 의도적으로 FK를 두지 않는다.
     */
    @Column(name = "senior_id", nullable = false)
    private Long seniorId;

    @Column(name = "family_id", nullable = false)
    private Long familyId;

    @Column(name = "senior_name", nullable = false)
    private String seniorName;

    @Column(name = "withdrawn_at", nullable = false)
    private LocalDateTime withdrawnAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public void complete(LocalDateTime completedAt) {
        if (this.completedAt == null) {
            this.completedAt = completedAt;
        }
    }
}
