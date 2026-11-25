package com.example.senioron.domain.family.entity;

import com.example.senioron.common.entity.BaseEntity;
import com.example.senioron.domain.senior.entity.Senior;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "family_photo_senior",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_family_photo_senior_photo_senior",
                columnNames = {
                        "family_photo_id",
                        "senior_id"
                }
        ),
        indexes = @Index(
                name = "idx_family_photo_senior_senior_photo",
                columnList = "senior_id, family_photo_id"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class FamilyPhotoSenior extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "family_photo_senior_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_photo_id", nullable = false)
    private FamilyPhoto familyPhoto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "senior_id", nullable = false)
    private Senior senior;
}