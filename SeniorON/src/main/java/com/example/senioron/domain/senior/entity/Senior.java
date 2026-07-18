package com.example.senioron.domain.senior.entity;

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeniorRelation relation;

    /*
     * relation이 OTHER인 경우 사용자가 직접 입력한 관계
     * 예: 이모, 삼촌, 지인
     */
    private String customRelation;

    @Column(nullable = false)
    private LocalDate birth;

    @Column(nullable = false)
    private String phoneNumber;

    private String address;

    private String detailAddress;

    /*
     * 해당 시니어 정보를 등록한 사용자
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registered_by_user_id", nullable = false)
    private User registeredBy;

    public void updateProfile(
            String name,
            LocalDate birth,
            String phoneNumber,
            String address,
            String detailAddress
    ) {
        this.name = name;
        this.birth = birth;
        this.phoneNumber = phoneNumber;
        this.address = address;
        this.detailAddress = detailAddress;
    }

}