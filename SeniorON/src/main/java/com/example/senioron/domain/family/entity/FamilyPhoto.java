package com.example.senioron.domain.family.entity;

import com.example.senioron.common.entity.BaseEntity;
import com.example.senioron.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.*;

@Entity
@Table(
        name = "family_photo",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_family_photo_user_idempotency",
                        columnNames = {
                                "users_id",
                                "idempotency_key"
                        }
                ),
                @UniqueConstraint(
                        name = "uk_family_photo_image_key",
                        columnNames = "image_key"
                )
        }
)
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
    @JoinColumn(name = "users_id")
    private User user;

    @Column(name = "image_key", nullable = false)
    private String imageKey;

    @Column(name = "thumbnail_key")
    private String thumbnailKey;

    @Column(name = "description", length = 30)
    private String description;

    @Column(name = "idempotency_key", length = 36)
    private String idempotencyKey;
}
