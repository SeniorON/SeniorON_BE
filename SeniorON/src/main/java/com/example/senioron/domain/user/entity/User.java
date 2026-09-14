package com.example.senioron.domain.user.entity;

import com.example.senioron.common.entity.BaseEntity;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyMember;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "users_id")
    private Long usersId;

    @OneToMany(mappedBy = "user")
    @Builder.Default
    private List<FamilyMember> familyMembers = new ArrayList<>();

    @Transient
    private Family family;

    @Column(unique = true)
    private String loginId;

    @Column(unique = true)
    private String email;

    private String password;

    @Column(nullable = false)
    private String name;

    @Column(nullable = true)
    private LocalDate birth;

    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Transient
    private ManagerType managerType;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "profile_image_key")
    private String profileImageKey;

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    @Column(name = "service_terms_agreed")
    private Boolean serviceTermsAgreed;

    @Column(name = "privacy_policy_agreed")
    private Boolean privacyPolicyAgreed;

    @Column(name = "age_over_14_agreed")
    private Boolean ageOver14Agreed;

    @Column(name = "marketing_agreed")
    private Boolean marketingAgreed;

    public void updateRole(Role role) {
        this.role = role;
    }

    public void updateFamily(Family family) {
        this.family = family;
    }

    public void updateManagerType(ManagerType managerType) {
        this.managerType = managerType;
    }

    public void removeFromFamily() {
        this.family = null;
        this.managerType = null;
        this.familyMembers.clear();
    }

    public Family getFamily() {
        if (family != null) {
            return family;
        }

        return familyMembers.stream()
                .min(Comparator.comparing(FamilyMember::getId))
                .map(FamilyMember::getFamily)
                .orElse(null);
    }

    public ManagerType getManagerType() {
        if (managerType != null) {
            return managerType;
        }

        return familyMembers.stream()
                .min(Comparator.comparing(FamilyMember::getId))
                .map(FamilyMember::getManagerType)
                .orElse(null);
    }

    public void updateName(String name) {
        this.name = name;
    }

    public void updatePassword(String password) {
        this.password = password;
    }

    public void updateProfileImageKey(String profileImageKey) {
        this.profileImageKey = profileImageKey;
    }

    public void withdraw(
            String anonymizedLoginId,
            String anonymizedEmail,
            String anonymizedPassword,
            LocalDateTime withdrawnAt
    ) {
        this.loginId = anonymizedLoginId;
        this.email = anonymizedEmail;
        this.name = "탈퇴회원";
        this.phoneNumber = null;
        this.password = anonymizedPassword;
        this.birth = null;
        this.profileImageKey = null;
        this.status = UserStatus.WITHDRAWN;
        this.withdrawnAt = withdrawnAt;
        removeFromFamily();
    }
}
