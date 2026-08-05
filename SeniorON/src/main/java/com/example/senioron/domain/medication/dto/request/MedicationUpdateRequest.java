package com.example.senioron.domain.medication.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "복약 수정 요청")
public class MedicationUpdateRequest {

    @NotBlank
    @Schema(
            description = "수정할 복약 그룹 ID",
            example = "f2b47b9d-962d-430b-b328-d25ed693acaf"
    )
    private String medicationGroupId;

    @NotBlank
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

    @NotEmpty
    @Schema(
            description = "복용 시간 목록, HH:mm 형식",
            example = "[\"08:00\", \"20:00\"]"
    )
    private List<
            @NotBlank
            @Pattern(
                    regexp = "^([01]\\d|2[0-3]):[0-5]\\d$"
            )
                    String
            > medicineTimes;

    @NotBlank
    @Pattern(
            regexp = "^\\d{4}-\\d{2}-\\d{2}$"
    )
    @Schema(
            description = "복용 시작일, yyyy-MM-dd 형식",
            example = "2026-08-05"
    )
    private String startDate;

    @NotBlank
    @Pattern(
            regexp = "^(DAILY|WEEKLY|MONTHLY)$"
    )
    @Schema(
            description = "반복 유형",
            allowableValues = {
                    "DAILY",
                    "WEEKLY",
                    "MONTHLY"
            },
            example = "WEEKLY"
    )
    private String repeatType;

    @NotNull
    @Min(1)
    @Schema(
            description = "반복 간격",
            example = "2"
    )
    private Integer repeatInterval;

    @Schema(
            description = "주간 반복 요일 목록. WEEKLY일 때 필수",
            example = "[\"월\", \"수\", \"금\"]"
    )
    private List<
            @Pattern(
                    regexp = "^(월|화|수|목|금|토|일)$"
            )
                    String
            > medicineDays;

    @NotBlank
    @Pattern(
            regexp = "^(ONGOING|DURATION|END_DATE)$"
    )
    @Schema(
            description = "복용 종료 조건",
            allowableValues = {
                    "ONGOING",
                    "DURATION",
                    "END_DATE"
            },
            example = "DURATION"
    )
    private String repeatEndType;

    @Min(1)
    @Schema(
            description = "주 단위 복용 기간. repeatEndType이 DURATION일 때 필수이며 MONTHLY 반복에서는 사용할 수 없음",
            example = "4",
            nullable = true
    )
    private Integer durationWeeks;

    @Pattern(
            regexp = "^\\d{4}-\\d{2}-\\d{2}$"
    )
    @Schema(
            description = "복용 종료일. repeatEndType이 END_DATE일 때 필수, yyyy-MM-dd 형식",
            example = "2026-09-01",
            nullable = true
    )
    private String endDate;
}