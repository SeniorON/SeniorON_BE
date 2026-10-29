package com.example.senioron.domain.family.entity;

import com.example.senioron.common.entity.BaseEntity;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.senior.entity.SeniorRelation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(
        name = "family_member",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_family_member_user_family",
                columnNames = {"users_id", "family_id"}
        )
)
public class FamilyMember extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "family_member_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "users_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_id", nullable = false)
    private Family family;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ManagerType managerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation", nullable = true)
    private SeniorRelation relation;

    @Column(name = "custom_relation", nullable = true)
    private String customRelation;

    public void updateManagerType(ManagerType managerType) {
        this.managerType = managerType;
    }

    public void updateRelation(
            SeniorRelation relation,
            String customRelation
    ) {
        this.relation = relation;
        this.customRelation = relation == SeniorRelation.OTHER
                ? customRelation
                : null;
    }
}


