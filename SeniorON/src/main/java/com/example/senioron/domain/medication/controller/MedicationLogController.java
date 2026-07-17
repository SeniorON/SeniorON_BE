package com.example.senioron.domain.medication.controller;

import com.example.senioron.global.apiPayload.response.Response;
import com.example.senioron.domain.medication.dto.response.MedicationScheduleResponse;
import com.example.senioron.domain.medication.service.MedicationLogService;
import com.example.senioron.domain.user.entity.User;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "복약 관리 API", description = "복약 로그 및 일정 관련 API")
@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/v1/medications/schedules")
public class MedicationLogController {

    private final MedicationLogService medicationLogService;

    @GetMapping
    @Operation(
            summary = "일일 복약 일정 조회 API",
            description = "로그인한 유저의 특정 날짜(Date)에 예정된 복약 로그 리스트를 계획된 시간(Time) 오름차순으로 조회합니다. (JWT 토큰 인증 필요)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "일정 조회 성공"),
            @ApiResponse(responseCode = "400", description = "날짜 누락 등 잘못된 요청", content = @Content(schema = @Schema(implementation = Response.class)))
    })
    public Response<List<MedicationScheduleResponse>> getDailyMedicationSchedules(
            @Parameter(hidden = true) @AuthenticationPrincipal User user,
            @Parameter(
                    description = "조회할 날짜 (형식: YYYY-MM-DD, 필수)",
                    schema = @Schema(type = "string", format = "date", example = "2026-07-17")
            )
            @RequestParam @NotNull(message = "날짜는 필수 입력값입니다.")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        Long userId = user.getUsersId();
        List<MedicationScheduleResponse> responses = medicationLogService.getDailyMedicationSchedules(userId, date);
        return Response.ok(responses);
    }
}