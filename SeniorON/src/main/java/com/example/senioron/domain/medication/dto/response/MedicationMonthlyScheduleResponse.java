package com.example.senioron.domain.medication.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Schema(
        description = "월별 복약 일정 날짜 응답"
)
public class MedicationMonthlyScheduleResponse {

    @Schema(
            description = "조회 연도",
            example = "2026"
    )
    private Integer year;

    @Schema(
            description = "조회 월",
            example = "7"
    )
    private Integer month;

    @ArraySchema(
            arraySchema = @Schema(
                    description = "복약 일정이 존재하는 날짜 목록"
            ),
            schema = @Schema(
                    type = "string",
                    format = "date",
                    example = "2026-07-01"
            )
    )
    private List<LocalDate> scheduledDates;
}