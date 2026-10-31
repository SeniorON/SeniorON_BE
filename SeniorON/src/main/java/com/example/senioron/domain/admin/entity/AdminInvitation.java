package com.example.senioron.domain.admin.entity;

import com.example.senioron.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(
        name = "admin_invitation",
        uniqueConstraints = @UniqueConstraint(name = "uk_admin_invitation_token_hash", columnNames = "token_hash"),
        indexes = @Index(name = "idx_admin_invitation_admin_type", columnList = "admin_id, type")
)
@AttributeOverrides({
        @AttributeOverride(name = "createdAt", column = @Column(name = "created_at", nullable = false, updatable = false)),
        @AttributeOverride(name = "updatedAt", column = @Column(name = "updated_at", nullable = false))
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AdminInvitation extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id", nullable = false)
    private Admin admin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AdminInvitationType type;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "invalidated_at")
    private LocalDateTime invalidatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private Admin createdBy;

    public boolean isUsable(LocalDateTime now) {
        return usedAt == null && invalidatedAt == null && expiresAt.isAfter(now);
    }

    public void use(LocalDateTime now) {
        this.usedAt = now;
    }

    public void invalidate(LocalDateTime now) {
        this.invalidatedAt = now;
    }
}
