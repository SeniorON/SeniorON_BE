package com.example.senioron.domain.home.dto.response;

import com.example.senioron.domain.hospital.entity.Hospital;
import com.example.senioron.domain.user.entity.ManagerType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalTime;

@Schema(description = "오늘 병원 일정 상세 목록 응답")
public record TodayHospitalListResponse(

        @Schema(description = "병원 일정 ID", example = "1")
        Long hospitalId,

        @Schema(description = "병원 이름", example = "서울대학교병원")
        String hospitalName,

        @Schema(description = "진료 과목", example = "내과")
        String department,

        @Schema(description = "진료 날짜", example = "2026-07-25")
        LocalDate scheduleDate,

        @Schema(description = "진료 시간", example = "10:30")
        LocalTime scheduleTime,

        @Schema(description = "알림 설정 타입", example = "DAY_BEFORE")
        String reminderType,

        @Schema(
                description = "일정을 등록한 담당자 유형",
                example = "PRIMARY"
        )
        ManagerType registeredBy
) {

    public static TodayHospitalListResponse from(
            Hospital hospital
    ) {

        return new TodayHospitalListResponse(
                hospital.getHospital_id(),
                hospital.getHospitalName(),
                hospital.getDepartment(),
                hospital.getScheduleDate(),
                hospital.getScheduleTime(),
                hospital.getReminderType() == null
                        ? null
                        : hospital.getReminderType().name(),
                hospital.getUser().getManagerType()
        );
    }
}