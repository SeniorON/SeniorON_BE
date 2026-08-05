package com.example.senioron.domain.medication.entity;

import com.example.senioron.common.entity.BaseEntity;
import com.example.senioron.domain.user.entity.User;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "medication",
        indexes = {
                @Index(
                        name = "idx_medication_user_effective",
                        columnList = "users_id, effective_from, effective_to"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Access(AccessType.FIELD)
public class Medication extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long medication_id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "users_id", nullable = false)
    private User user;

    private String ingredientName;

    @Column(nullable = false)
    private String medicineName;

    @Column(nullable = false)
    private LocalTime medicineTime;

    @Column(nullable = false)
    private String medicineDays;

    @Column(name = "medication_group_id", nullable = false)
    private String medicationGroupId;

    @Column(name = "schedule_start_date", nullable = false)
    private LocalDate scheduleStartDate;

    @Column(name = "schedule_end_date")
    private LocalDate scheduleEndDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "repeat_type", nullable = false, length = 20)
    private MedicationRepeatType repeatType;

    @Column(name = "repeat_interval", nullable = false)
    private Integer repeatInterval;

    @Enumerated(EnumType.STRING)
    @Column(name = "repeat_end_type", nullable = false, length = 20)
    private MedicationRepeatEndType repeatEndType;

    @Column(name = "duration_weeks")
    private Integer durationWeeks;

    @Column(name = "effective_from", nullable = false)
    private LocalDateTime effectiveFrom;

    @Column(name = "effective_to")
    private LocalDateTime effectiveTo;

    public void endMedication(LocalDateTime endedAt) {
        this.effectiveTo = endedAt;
    }
}