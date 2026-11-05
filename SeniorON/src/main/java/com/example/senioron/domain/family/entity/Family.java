package com.example.senioron.domain.family.entity;

import com.example.senioron.domain.senior.entity.Senior;
import jakarta.persistence.*;
import lombok.*;
import com.example.senioron.common.entity.BaseEntity;

import java.time.LocalDateTime;
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

    @Column(name = "first_senior_connected_at", nullable = true)
    private LocalDateTime firstSeniorConnectedAt;

    @OneToMany(mappedBy = "family")
    @Builder.Default
    private List<FamilyMember> familyMembers = new ArrayList<>();

    @OneToOne(mappedBy = "family", fetch = FetchType.LAZY)
    private Senior senior;

    public void recordFirstSeniorConnectedAt(LocalDateTime connectedAt) {
        if (firstSeniorConnectedAt == null) {
            firstSeniorConnectedAt = connectedAt;
        }
    }

    public void invalidateSeniorCode() {
        seniorCode = null;
    }

    /**
     * Senior 삭제 전에 양방향 연관관계의 역방향 참조를 제거한다.
     * 실제 FK는 Senior.family가 소유하므로 이 메서드는 DB FK를 갱신하지 않는다.
     */
    public void unlinkSenior() {
        senior = null;
    }
}
