package com.example.senioron.domain.admin.entity;

import com.example.senioron.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "admin", uniqueConstraints = @UniqueConstraint(name = "uk_admin_email", columnNames = "email"))
@AttributeOverrides({
        @AttributeOverride(name = "createdAt", column = @Column(name = "created_at", nullable = false, updatable = false)),
        @AttributeOverride(name = "updatedAt", column = @Column(name = "updated_at", nullable = false))
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Admin extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "admin_id")
    private Long adminId;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(length = 255)
    private String password;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "admin_role", nullable = false, length = 20)
    private AdminRole adminRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AdminStatus status;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    public void activate(String encodedPassword) {
        this.password = encodedPassword;
        this.status = AdminStatus.ACTIVE;
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void recordLogin(LocalDateTime now) {
        this.lastLoginAt = now;
    }

    public boolean isActive() {
        return status == AdminStatus.ACTIVE;
    }
}
