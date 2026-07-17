package com.example.senioron.domain.medication.dto.request;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MedicationScheduleRequest {

    @Schema(description = "조회 날짜 (YYYY-MM-DD)", example = "2026-07-17")
    @NotBlank(message = "날짜는 필수 입력 값입니다.")
    @Pattern(
            regexp = "^\\d{4}-\\d{2}-\\d{2}$",
            message = "날짜 형식은 YYYY-MM-DD 이어야 합니다. (예: 2026-07-17)"
    )
    private String date;
}