package com.example.senioron.domain.home.entity;

import com.example.senioron.common.entity.BaseEntity;
import com.example.senioron.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(
        name = "home_setting",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_home_setting_user",
                        columnNames = "users_id"
                )
        }
)
public class HomeSetting extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long homeSettingId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "users_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    private MusicApp musicApp;

    public void updateMusicApp(MusicApp musicApp) {
        this.musicApp = musicApp;
    }
}