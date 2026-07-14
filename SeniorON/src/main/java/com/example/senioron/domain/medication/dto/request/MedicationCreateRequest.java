package com.example.senioron.domain.medication.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.List;

@Getter
@NoArgsConstructor
@Schema(description = "약 복용 정보 등록 요청")
public class MedicationCreateRequest {

    @NotBlank(message = "약 이름은 필수입니다.")
    @Schema(description = "약 이름", example = "아스피린")
    private String medicineName;

    @Schema(description = "성분명 (선택 입력)", example = "아세틸살리실산 500mg")
    private String ingredientName;

    @NotEmpty(message = "복용 시간은 최소 하나 이상 지정해야 합니다.")
    // 💡 네 서비스 로직(for문)에서 List<LocalTime>을 쓰기 때문에 이렇게 리스트 예시로 넣어줘야 해!
    @Schema(description = "복용 시간 목록", example = "[\"08:30\", \"19:00\"]")
    private List<LocalTime> medicineTimes;

    @NotEmpty(message = "복용 요일은 최소 하나 이상 지정해야 합니다.")
    @Schema(description = "복용 요일 목록 (월, 화, 수, 목, 금, 토, 일 또는 MON, TUE 등)", example = "[\"월\", \"수\", \"금\"]")
    private List<String> medicineDays;
}