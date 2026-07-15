package com.example.senioron.domain.user.entity;

import com.example.senioron.common.entity.BaseEntity;
import com.example.senioron.domain.family.entity.Family;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_id")
    private Family family;

    @Column(unique = true)
    private String loginId;

    @Column(unique = true)
    private String email;

    private String password;

    @Column(nullable = false)
    private String name;

    private LocalDate birth;

    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Enumerated(EnumType.STRING)
    private ManagerType managerType;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    private String fcmToken;

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
    }

    public void updateFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }

    public void updateName(String name) {
        this.name = name;
    }

    public void updatePassword(String password) {
        this.password = password;
    }
}
