package com.example.senioron.domain.hospital.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HospitalDetailResponse {

    @Schema(description = "병원 일정 고유 ID", example = "1")
    private Long hospitalId;

    @Schema(description = "병원 이름", example = "서울대학교병원")
    private String hospitalName;

    @Schema(description = "진료 과목", example = "내과")
    private String department;

    @Schema(description = "진료 날짜", example = "2026-06-19")
    private LocalDate scheduleDate;

    @Schema(description = "진료 시간", example = "10:30")
    private LocalTime scheduleTime;

    @Schema(description = "알림 타입", example = "BEFORE_1_DAY")
    private String reminderType;
}