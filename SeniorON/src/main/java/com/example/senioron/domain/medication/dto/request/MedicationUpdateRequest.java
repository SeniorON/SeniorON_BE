package com.example.senioron.domain.medication.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "약 수정 요청 DTO")
public class MedicationUpdateRequest {

    @NotBlank(message = "수정할 복약 그룹 ID는 필수입니다.")
    @Schema(description = "복약 그룹 ID (UUID)", example = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")
    private String medicationGroupId; // 💡 오타 수정 및 String 타입 일치!

    @NotBlank(message = "약 이름은 필수입니다.")
    @Schema(description = "약 이름", example = "아스피린")
    private String medicineName;

    @Schema(description = "성분명 (선택)", example = "아세틸살리실산")
    private String ingredientName;

    @NotEmpty(message = "복용 요일은 최소 하나 이상 선택해야 합니다.")
    @Schema(description = "복용 요일 목록", example = "[\"MON\", \"WED\", \"FRI\"]")
    private List<String> medicineDays;

    @NotEmpty(message = "복용 시간은 최소 하나 이상 등록해야 합니다.")
    @Schema(description = "복용 시간 목록 (HH:mm 형식)", example = "[\"08:00\", \"13:00\", \"19:00\"]")
    private List<@NotBlank(message = "복용 시간은 빈 값일 수 없습니다.") String> medicineTimes;
}