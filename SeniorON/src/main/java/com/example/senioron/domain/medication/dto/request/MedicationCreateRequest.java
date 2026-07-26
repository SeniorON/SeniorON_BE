package com.example.senioron.domain.medication.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "약 복용 정보 등록 요청")
public class MedicationCreateRequest {

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
            example = "[\"08:30\", \"19:00\"]"
    )
    private List<
            @NotNull(message = "복용 시간 값은 필수입니다.")
            @Pattern(
                    regexp = "^(0[0-9]|1[0-9]|2[0-3]):[0-5][0-9]$",
                    message = "복용 시간은 올바른 24시간 형식(HH:mm)이어야 합니다. (예: 08:30)"
            )
                    String
            > medicineTimes;

    @NotEmpty(message = "복용 요일은 최소 하나 이상 지정해야 합니다.")
    @Schema(
            description = "복용 요일 목록 (월, 화, 수, 목, 금, 토, 일)",
            example = "[\"월\", \"수\", \"금\"]"
    )
    private List<
            @NotBlank(message = "복용 요일 값은 필수입니다.")
            @Pattern(
                    regexp = "^[월화수목금토일]$",
                    message = "요일은 '월', '화', '수', '목', '금', '토', '일' 중 하나여야 합니다."
            )
                    String
            > medicineDays;
}