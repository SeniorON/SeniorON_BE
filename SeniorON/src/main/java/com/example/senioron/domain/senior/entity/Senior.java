package com.example.senioron.domain.senior.entity;

import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "seniors")
public class Senior {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long seniorId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private LocalDate birth;

    @Column(nullable = false)
    private String phoneNumber;

    private String address;

    private String detailAddress;

    private Double latitude;

    private Double longitude;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_id", nullable = false)
    private Family family;

    /*
     * 해당 시니어 정보를 등록한 사용자
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registered_by_user_id", nullable = false)
    private User registeredBy;

    /*
     * 해당 시니어 본인의 실제 PARENT 계정
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_user_id", unique = true)
    private User parentUser;

    public void linkParentUser(User parentUser) {
        this.parentUser = parentUser;
    }

    public void updateProfile(
            String name,
            LocalDate birth,
            String phoneNumber,
            String address,
            String detailAddress,
            Double latitude,
            Double longitude
    ) {
        this.name = name;
        this.birth = birth;
        this.phoneNumber = phoneNumber;
        this.address = address;
        this.detailAddress = detailAddress;
        this.latitude = latitude;
        this.longitude = longitude;
    }

}
