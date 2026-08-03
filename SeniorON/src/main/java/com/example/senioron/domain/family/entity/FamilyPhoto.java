package com.example.senioron.domain.family.entity;

import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.*;
import lombok.*;
import com.example.senioron.common.entity.BaseEntity;

@Entity
@Table(name = "family_photo", uniqueConstraints = {
        @UniqueConstraint(name = "uk_family_photo_user_idempotency", columnNames = {"users_id", "idempotency_key"})},
        indexes = {
                @Index(name = "idx_family_photo_family_created", columnList = "family_id, created_at, family_photo_id"),
                @Index(name = "idx_family_photo_family_user", columnList = "family_id, users_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class FamilyPhoto extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="family_photo_id")
    private Long familyPhotoId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_id")
    private Family family;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "users_id")
    private User user;

    @Column(name = "image_key", nullable = false)
    private String imageKey;

    @Column(name = "description", length = 30)
    private String description;

    @Builder.Default
    @Column(name = "viewed_by_parent", nullable = false)
    private boolean viewedByParent = false;

    public void markAsViewedByParent() {
        this.viewedByParent = true;
    }

    @Column(name = "idempotency_key", length = 36)
    private String idempotencyKey;
}
