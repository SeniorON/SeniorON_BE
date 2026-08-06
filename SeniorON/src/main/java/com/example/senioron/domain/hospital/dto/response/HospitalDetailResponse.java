package com.example.senioron.domain.hospital.dto.response;

import com.example.senioron.domain.hospital.entity.HospitalReminderType;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "병원 일정 상세 조회 응답")
public class HospitalDetailResponse {

    @Schema(
            description = "병원 일정 고유 ID",
            example = "1"
    )
    private Long hospitalId;

    @Schema(
            description = "병원 이름",
            example = "서울대학교병원"
    )
    private String hospitalName;

    @Schema(
            description = "진료 과목",
            example = "내과"
    )
    private String department;

    @Schema(
            description = "진료 날짜",
            example = "2026-06-19"
    )
    private LocalDate scheduleDate;

    @JsonFormat(pattern = "HH:mm")
    @Schema(
            description = "진료 시간",
            example = "10:30"
    )
    private LocalTime scheduleTime;

    @Schema(
            description = "알림 설정 타입",
            allowableValues = {
                    "DAY_BEFORE",
                    "SAME_DAY",
                    "NONE"
            },
            example = "DAY_BEFORE"
    )
    private HospitalReminderType reminderType;
}