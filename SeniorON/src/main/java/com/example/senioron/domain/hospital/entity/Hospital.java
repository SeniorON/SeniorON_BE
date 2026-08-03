package com.example.senioron.domain.hospital.entity;

import com.example.senioron.common.entity.BaseEntity;
import com.example.senioron.domain.device.entity.Device;
import com.example.senioron.domain.user.entity.User;
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
        name = "hospital",
        indexes = {
                @Index(
                        name = "idx_hospital_user_schedule",
                        columnList = "users_id, schedule_date"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Hospital extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long hospital_id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "users_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;

    @Column(nullable = false)
    private String hospitalName;

    @Column(nullable = false)
    private String department;

    @Column(nullable = false)
    private LocalDate scheduleDate;

    @Column(nullable = false)
    private LocalTime scheduleTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HospitalReminderType reminderType;

    @Column(name = "reminder_sent_at")
    private LocalDateTime reminderSentAt;

    public void updateHospital(
            String hospitalName,
            String department,
            LocalDate scheduleDate,
            LocalTime scheduleTime,
            HospitalReminderType reminderType
    ) {
        this.hospitalName = hospitalName;
        this.department = department;
        this.scheduleDate = scheduleDate;
        this.scheduleTime = scheduleTime;
        this.reminderType = reminderType;
        this.reminderSentAt = null;
    }
}