package com.example.senioron.domain.senior.entity;

import com.example.senioron.domain.user.entity.User;
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
        name = "user_seniors",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_senior",
                columnNames = {"users_id", "senior_id"}
        )
)
public class UserSenior {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userSeniorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "users_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "senior_id", nullable = false)
    private Senior senior;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeniorRelation relation;

    private String customRelation;

    public void updateRelation(SeniorRelation relation, String customRelation) {
        this.relation = relation;
        this.customRelation = customRelation;
    }
}
