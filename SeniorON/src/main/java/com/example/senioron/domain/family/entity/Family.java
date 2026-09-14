package com.example.senioron.domain.family.entity;

import com.example.senioron.domain.senior.entity.Senior;
import jakarta.persistence.*;
import lombok.*;
import com.example.senioron.common.entity.BaseEntity;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "family")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Family extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="family_id")
    private Long familyId;

    @Column(name = "senior_code", unique = true)
    private String seniorCode;

    @OneToMany(mappedBy = "family")
    @Builder.Default
    private List<FamilyMember> familyMembers = new ArrayList<>();

    @OneToMany(mappedBy = "family")
    @Builder.Default
    private List<PhotoGroupFamily> photoGroupFamilies = new ArrayList<>();

    @OneToOne(mappedBy = "family", fetch = FetchType.LAZY)
    private Senior senior;
}
