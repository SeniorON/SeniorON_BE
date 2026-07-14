package com.example.senioron.domain.hospital.dto.request;

import com.example.senioron.domain.hospital.entity.HospitalReminderType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "병원 일정 등록 요청")
public class HospitalCreateRequest {

    @NotBlank(message = "병원 이름은 필수입니다.")
    @Schema(description = "병원 이름", example = "서울대학교병원")
    private String hospitalName;

    @NotBlank(message = "진료 과목은 필수입니다.")
    @Schema(description = "진료 과목", example = "내과")
    private String department;

    @NotNull(message = "진료 날짜는 필수입니다.")
    @Schema(description = "진료 날짜", example = "2026-07-14")
    private LocalDate scheduleDate;

    @NotNull(message = "진료 시간은 필수입니다.")
    @Schema(description = "진료 시간", example = "10:30")
    private LocalTime scheduleTime;

    @NotNull(message = "알림 설정은 필수입니다.")
    @Schema(description = "알림 설정 타입", example = "DAY_BEFORE")
    private HospitalReminderType reminderType;

}