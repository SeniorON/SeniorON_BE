package com.example.senioron.domain.hospital.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonFormat;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "다가오는 병원 일정 날짜 카드 응답")
public class HospitalUpcomingResponse {

    @Schema(
            description = "진료 일정 날짜",
            example = "2026-08-03"
    )
    private LocalDate scheduleDate;

    @ArraySchema(
            arraySchema = @Schema(
                    description = "해당 날짜의 진료 일정 목록"
            ),
            schema = @Schema(
                    implementation = HospitalDetailResponse.class
            )
    )
    private List<HospitalDetailResponse> schedules;
}