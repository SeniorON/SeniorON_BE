package com.example.senioron.domain.medication.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
@Schema(description = "약 목록 조회 응답")
public class MedicationReadResponse {

    @Schema(
            description = "복용 시간별로 생성된 약 ID 목록",
            example = "[1, 2]"
    )
    private List<Long> medicationIds;

    @Schema(
            description = "동일한 약의 복용 시간들을 묶는 약 그룹 ID",
            example = "f2b47b9d-962d-430b-b328-d25ed693acaf"
    )
    private String medicationGroupId;

    @Schema(
            description = "약 이름",
            example = "아스피린"
    )
    private String medicineName;

    @Schema(
            description = "성분명",
            example = "아세틸살리실산 500mg",
            nullable = true
    )
    private String ingredientName;

    @Schema(
            description = "복용 시간 목록",
            example = "[\"08:00\", \"14:00\"]"
    )
    private List<String> medicineTimes;

    @Schema(
            description = "복용 시작일",
            example = "2026-08-04"
    )
    private String startDate;

    @Schema(
            description = "반복 유형",
            example = "WEEKLY",
            allowableValues = {"DAILY", "WEEKLY", "MONTHLY"}
    )
    private String repeatType;

    @Schema(
            description = "반복 간격",
            example = "2"
    )
    private Integer repeatInterval;

    @Schema(
            description = "복용 요일 목록. 매주 반복이 아닌 경우 빈 목록으로 반환합니다.",
            example = "[\"월\", \"수\", \"금\"]"
    )
    private List<String> medicineDays;

    @Schema(
            description = "복용 종료 방식",
            example = "DURATION",
            allowableValues = {"ONGOING", "DURATION", "END_DATE"}
    )
    private String repeatEndType;

    @Schema(
            description = "복용 기간(주). 기간 지정 방식이 아닌 경우 null입니다.",
            example = "4",
            nullable = true
    )
    private Integer durationWeeks;

    @Schema(
            description = "실제 복용 종료일. 계속 복용인 경우 null입니다.",
            example = "2026-08-31",
            nullable = true
    )
    private String endDate;
}