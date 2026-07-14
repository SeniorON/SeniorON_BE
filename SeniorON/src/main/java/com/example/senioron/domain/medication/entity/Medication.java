package com.example.senioron.domain.medication.entity;

import com.example.senioron.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.*;
import lombok.*;
import com.example.senioron.common.entity.BaseEntity;
import jakarta.persistence.Column;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Medication extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long medication_id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "users_id")
    private User user;

    private String ingredientName;

    private String medicineName;

    private LocalTime medicineTime;

    private String medicineDays;

    @Column(name = "medication_group_id", nullable = false)
    private String medicationGroupId;

}
