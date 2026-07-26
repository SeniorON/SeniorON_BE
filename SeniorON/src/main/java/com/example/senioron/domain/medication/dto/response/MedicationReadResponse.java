package com.example.senioron.domain.medication.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Schema(description = "약 목록 조회 응답")
public class MedicationReadResponse {

    @Schema(description = "약 ID", example = "1")
    private Long medicationId;

    @Schema(
            description = "동일한 약의 복용 시간들을 묶는 약 그룹 ID",
            example = "f2b47b9d-962d-430b-b328-d25ed693acaf"
    )
    private String medicationGroupId;

    @Schema(description = "약 이름", example = "아스피린")
    private String medicineName;

    @Schema(description = "성분명", example = "아세틸살리실산 500mg")
    private String ingredientName;

    @Schema(description = "복용 시간", example = "오전 8시")
    private String medicineTime;

    @Schema(description = "복용 요일", example = "월, 수, 금")
    private String medicineDays;
}