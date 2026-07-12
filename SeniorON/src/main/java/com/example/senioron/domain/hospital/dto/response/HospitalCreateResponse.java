package com.example.senioron.domain.hospital.dto.response;

import com.example.senioron.domain.hospital.entity.HospitalReminderType;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class HospitalCreateResponse {

    private Long hospitalId;

    private String hospitalName;

    private String department;

    private LocalDate scheduleDate;

    private LocalTime scheduleTime;

    private HospitalReminderType reminderType;
}