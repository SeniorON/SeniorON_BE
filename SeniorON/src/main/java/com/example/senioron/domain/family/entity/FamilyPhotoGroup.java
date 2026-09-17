package com.example.senioron.domain.family.entity;

import com.example.senioron.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "family_photo_group",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_family_photo_group_photo",
                columnNames = {
                        "family_photo_id",
                        "photo_group_id"
                }
        ),
        indexes = @Index(
                name = "idx_family_photo_group_group_photo",
                columnList = "photo_group_id, family_photo_id"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class FamilyPhotoGroup extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "family_photo_group_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_photo_id", nullable = false)
    private FamilyPhoto familyPhoto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "photo_group_id", nullable = false)
    private PhotoGroup photoGroup;
}