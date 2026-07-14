package com.example.senioron.domain.socialaccount.entity;

import com.example.senioron.common.entity.BaseEntity;
import com.example.senioron.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;


@Entity
@Table(
        name = "social_accounts",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_social_provider_provider_id",
                        columnNames = {"provider", "provider_id"}
                ),
                @UniqueConstraint(
                        name = "uk_social_user_provider",
                        columnNames = {"users_id", "provider"}
                )
        }
)



@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SocialAccount extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "social_account_id")
    private Long socialAccountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "users_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoginProvider provider;

    @Column(name = "provider_id", nullable = false)
    private String providerId;


}
