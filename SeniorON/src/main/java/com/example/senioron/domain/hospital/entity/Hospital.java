package com.example.senioron.domain.hospital.entity;

import com.example.senioron.domain.device.entity.Device;
import com.example.senioron.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.*;
import lombok.*;
import com.example.senioron.common.entity.BaseEntity;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Hospital extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long hospital_id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "users_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;

    private String title;

    private LocalDate scheduleDate;

    private LocalTime scheduleTime;

}
