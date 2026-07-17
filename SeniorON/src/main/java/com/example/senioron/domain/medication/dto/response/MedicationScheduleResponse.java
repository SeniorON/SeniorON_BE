package com.example.senioron.domain.medication.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "일일 복약 일정 조회 응답")
public class MedicationScheduleResponse {

    @Schema(description = "복약 로그 ID (미복용 상태면 null)", example = "1", nullable = true)
    private Long medicationLogId;

    @Schema(description = "약 이름", example = "아스피린")
    private String medicineName;

    @Schema(description = "복용 예정 시간", example = "08:30:00", type = "string")
    private LocalTime plannedTime;

    @Schema(description = "복용 여부", example = "false")
    private Boolean isTaken;
}