package com.example.senioron.domain.hospital.dto.response;

import com.example.senioron.domain.hospital.entity.HospitalReminderType;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
@Schema(description = "병원 일정 등록 응답")
public class HospitalCreateResponse {

    @Schema(description = "병원 일정 고유 ID", example = "1")
    private Long hospitalId;

    @Schema(description = "병원 이름", example = "서울대학교병원")
    private String hospitalName;

    @Schema(description = "진료 과목", example = "내과")
    private String department;

    @Schema(description = "진료 날짜", example = "2026-07-14")
    private LocalDate scheduleDate;

    @JsonFormat(pattern = "HH:mm")
    @Schema(description = "진료 시간", example = "10:30")
    private LocalTime scheduleTime;

    @Schema(description = "알림 설정 타입", example = "DAY_BEFORE")
    private HospitalReminderType reminderType;
}