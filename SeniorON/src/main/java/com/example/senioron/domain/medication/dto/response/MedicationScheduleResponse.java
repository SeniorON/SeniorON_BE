package com.example.senioron.domain.medication.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
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

    @Schema(
            description = "복약 로그 ID",
            example = "1"
    )
    private Long medicationLogId;

    @Schema(
            description = "약 이름",
            example = "혈압약"
    )
    private String medicineName;

    @Schema(
            description = "성분명",
            example = "아암로디핀",
            nullable = true
    )
    private String ingredientName;

    @Schema(
            description = "복용 예정 날짜",
            example = "2026-07-28",
            type = "string",
            format = "date"
    )
    private LocalDate plannedDate;

    @Schema(
            description = "복용 예정 시간",
            example = "08:30:00",
            type = "string",
            format = "time"
    )
    private LocalTime plannedTime;

    @JsonFormat(pattern = "HH:mm")
    @Schema(
            description = "실제 복용 완료 시간. 미복용 상태인 경우 null",
            example = "14:00",
            type = "string",
            format = "time",
            nullable = true
    )
    private LocalTime takenTime;

    @Schema(
            description = "복용 여부",
            example = "false"
    )
    private Boolean isTaken;

    @Schema(
            description = "복약 상태",
            example = "SCHEDULED",
            allowableValues = {
                    "TAKEN",
                    "MISSED",
                    "SCHEDULED"
            }
    )
    private MedicationScheduleStatus status;
}