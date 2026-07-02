package com.example.senioron.domain.medication.entity;

import com.example.senioron.domain.device.entity.Device;
import com.example.senioron.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.*;
import lombok.*;
import com.example.senioron.common.entity.BaseEntity;

@Entity
@Table(name = "medication_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MedicationLog extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long medication_log_id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "users_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;

    private LocalDate plannedDate;

    private LocalTime plannedTime;

    private Boolean isTaken;

    private LocalDateTime takenAt;

}
