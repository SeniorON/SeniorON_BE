package com.example.senioron.domain.medication.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
@Schema(description = "복약 목록 조회 응답")
public class MedicationReadResponse {

    @Schema(
            description = "기존 클라이언트 호환용 대표 복약 ID",
            example = "1"
    )
    private Long medicationId;

    @Schema(
            description = "동일 복약 그룹의 복약 ID 목록",
            example = "[1, 2]"
    )
    private List<Long> medicationIds;

    @Schema(
            description = "복약 그룹 ID",
            example = "f2b47b9d-962d-430b-b328-d25ed693acaf"
    )
    private String medicationGroupId;

    @Schema(
            description = "약 이름",
            example = "혈압약"
    )
    private String medicineName;

    @Schema(
            description = "약 성분명",
            example = "암로디핀 5mg",
            nullable = true
    )
    private String ingredientName;

    @Schema(
            description = "기존 클라이언트 호환용 대표 복용 시간",
            example = "오전 8시"
    )
    private String medicineTime;

    @Schema(
            description = "동일 복약 그룹의 복용 시간 목록",
            example = "[\"08:00\", \"20:00\"]"
    )
    private List<String> medicineTimes;

    @Schema(
            description = "복용 시작일",
            example = "2026-08-05"
    )
    private String startDate;

    @Schema(
            description = "반복 유형",
            example = "WEEKLY"
    )
    private String repeatType;

    @Schema(
            description = "반복 간격",
            example = "2"
    )
    private Integer repeatInterval;

    @Schema(
            description = "기존 클라이언트 호환용 복용 요일 표시 문자열",
            example = "월, 수, 금"
    )
    private String medicineDays;

    @Schema(
            description = "복용 요일 목록",
            example = "[\"월\", \"수\", \"금\"]"
    )
    private List<String> medicineDayList;

    @Schema(
            description = "복용 종료 조건",
            example = "DURATION"
    )
    private String repeatEndType;

    @Schema(
            description = "주 단위 복용 기간",
            example = "4",
            nullable = true
    )
    private Integer durationWeeks;

    @Schema(
            description = "복용 종료일",
            example = "2026-09-01",
            nullable = true
    )
    private String endDate;
}