package com.example.senioron.domain.medication.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "약 복용 정보 수정 요청")
public class MedicationUpdateRequest {

    @NotBlank(message = "수정할 약 그룹 ID는 필수입니다.")
    @Schema(
            description = "약 그룹 ID",
            example = "f2b47b9d-962d-430b-b328-d25ed693acaf"
    )
    private String medicationGroupId;

    @NotBlank(message = "약 이름은 필수입니다.")
    @Schema(
            description = "약 이름",
            example = "아스피린"
    )
    private String medicineName;

    @Schema(
            description = "성분명 (선택 입력)",
            example = "아세틸살리실산 500mg"
    )
    private String ingredientName;

    @NotEmpty(message = "복용 시간은 최소 하나 이상 지정해야 합니다.")
    @Schema(
            description = "복용 시간 목록 (24시간 형식: HH:mm)",
            example = "[\"08:00\", \"13:00\", \"19:00\"]"
    )
    private List<
            @NotNull(message = "복용 시간 값은 필수입니다.")
            @Pattern(
                    regexp = "^(0[0-9]|1[0-9]|2[0-3]):[0-5][0-9]$",
                    message = "복용 시간은 올바른 24시간 형식(HH:mm)이어야 합니다. (예: 08:30)"
            )
                    String
            > medicineTimes;

    @NotBlank(message = "복용 시작일은 필수입니다.")
    @Pattern(
            regexp = "^\\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])$",
            message = "복용 시작일은 yyyy-MM-dd 형식이어야 합니다. (예: 2026-08-04)"
    )
    @Schema(
            description = "복용 시작일 (yyyy-MM-dd)",
            example = "2026-08-04"
    )
    private String startDate;

    @NotBlank(message = "반복 유형은 필수입니다.")
    @Pattern(
            regexp = "^(DAILY|WEEKLY|MONTHLY)$",
            message = "반복 유형은 DAILY, WEEKLY, MONTHLY 중 하나여야 합니다."
    )
    @Schema(
            description = "반복 유형",
            example = "WEEKLY",
            allowableValues = {"DAILY", "WEEKLY", "MONTHLY"}
    )
    private String repeatType;

    @NotNull(message = "반복 주기는 필수입니다.")
    @Min(value = 1, message = "반복 주기는 1 이상이어야 합니다.")
    @Schema(
            description = "반복 간격. 예를 들어 WEEKLY이고 값이 2이면 2주마다 반복합니다.",
            example = "2",
            minimum = "1"
    )
    private Integer repeatInterval;

    @Schema(
            description = "복용 요일 목록. WEEKLY 반복일 때 필수이며 DAILY, MONTHLY에서는 사용하지 않습니다.",
            example = "[\"월\", \"수\", \"금\"]"
    )
    private List<
            @NotBlank(message = "복용 요일 값은 비어 있을 수 없습니다.")
            @Pattern(
                    regexp = "^[월화수목금토일]$",
                    message = "요일은 '월', '화', '수', '목', '금', '토', '일' 중 하나여야 합니다."
            )
                    String
            > medicineDays;

    @NotBlank(message = "복용 종료 방식은 필수입니다.")
    @Pattern(
            regexp = "^(ONGOING|DURATION|END_DATE)$",
            message = "복용 종료 방식은 ONGOING, DURATION, END_DATE 중 하나여야 합니다."
    )
    @Schema(
            description = "복용 종료 방식",
            example = "DURATION",
            allowableValues = {"ONGOING", "DURATION", "END_DATE"}
    )
    private String repeatEndType;

    @Min(value = 1, message = "복용 기간은 1주 이상이어야 합니다.")
    @Schema(
            description = "복용 기간(주). repeatEndType이 DURATION일 때 필수입니다.",
            example = "4",
            nullable = true,
            minimum = "1"
    )
    private Integer durationWeeks;

    @Pattern(
            regexp = "^\\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])$",
            message = "복용 종료일은 yyyy-MM-dd 형식이어야 합니다. (예: 2026-08-30)"
    )
    @Schema(
            description = "복용 종료일. repeatEndType이 END_DATE일 때 필수입니다.",
            example = "2026-08-30",
            nullable = true
    )
    private String endDate;
}