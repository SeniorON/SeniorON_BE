package com.example.senioron.domain.medication.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
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
    @Schema(description = "약 그룹 ID", example = "f2b47b9d-962d-430b-b328-d25ed693acaf")
    private String medicationGroupId;

    @NotBlank(message = "약 이름은 필수입니다.")
    @Schema(description = "약 이름", example = "아스피린")
    private String medicineName;

    @Schema(description = "성분명 (선택 입력)", example = "아세틸살리실산 500mg")
    private String ingredientName;

    @NotEmpty(message = "복용 요일은 최소 하나 이상 지정해야 합니다.")
    @Schema(
            description = "복용 요일 목록 (MON, MONDAY, 월 등)",
            example = "[\"MON\", \"WED\", \"FRI\"]"
    )
    private List<
            @NotNull(message = "복용 요일 값은 필수입니다.")
            @Pattern(
                    regexp = "^(SUN|SUNDAY|MON|MONDAY|TUE|TUESDAY|WED|WEDNESDAY|THU|THURSDAY|FRI|FRIDAY|SAT|SATURDAY|일|월|화|수|목|금|토)$",
                    message = "요일은 올바른 영문 요일(예: MON, MONDAY) 또는 한글 요일(예: 월) 형식이어야 합니다."
            )
                    String
            > medicineDays;

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
}