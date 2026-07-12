package com.example.senioron.domain.hospital.dto.request;


import com.example.senioron.domain.hospital.entity.HospitalReminderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class HospitalCreateRequest {

    @NotBlank(message = "병원 이름은 필수입니다.")
    private String hospitalName;

    @NotBlank(message = "진료 과목은 필수입니다.")
    private String department;

    @NotNull(message = "진료 날짜는 필수입니다.")
    private LocalDate scheduleDate;

    @NotNull(message = "진료 시간은 필수입니다.")
    private LocalTime scheduleTime;

    @NotNull(message = "알림 설정은 필수입니다.")
    private HospitalReminderType reminderType;

}
