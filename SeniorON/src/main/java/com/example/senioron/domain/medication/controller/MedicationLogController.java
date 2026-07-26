package com.example.senioron.domain.medication.controller;

import com.example.senioron.domain.medication.dto.response.MedicationCheckResponse;
import com.example.senioron.domain.medication.dto.response.MedicationScheduleResponse;
import com.example.senioron.domain.medication.service.MedicationLogService;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.code.ResultCode;
import com.example.senioron.global.apiPayload.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "복약 관리 API",
        description = "복약 일정 조회 및 복약 체크 관련 API"
)
@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/v1")
public class MedicationLogController {

    private final MedicationLogService medicationLogService;

    @GetMapping("/medications/schedules")
    @Operation(
            summary = "본인 일일 복약 일정 조회 API",
            description = "부모 사용자가 본인의 특정 날짜 복약 일정을 예정 시간 오름차순으로 조회합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "일정 조회 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "날짜 누락 또는 잘못된 날짜 형식",
                    content = @Content(
                            schema = @Schema(
                                    implementation = Response.class
                            )
                    )
            )
    })
    public Response<List<MedicationScheduleResponse>> getDailyMedicationSchedules(
            @Parameter(hidden = true)
            @AuthenticationPrincipal User user,

            @Parameter(
                    description = "조회할 날짜",
                    required = true,
                    schema = @Schema(
                            type = "string",
                            format = "date",
                            example = "2026-07-25"
                    )
            )
            @RequestParam("date")
            @NotNull(message = "날짜는 필수 입력값입니다.")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date
    ) {
        List<MedicationScheduleResponse> responses =
                medicationLogService.getDailyMedicationSchedules(
                        user.getUsersId(),
                        user.getUsersId(),
                        date
                );

        return Response.ok(responses);
    }

    @GetMapping("/medications/parents/{parentUserId}/schedules")
    @Operation(
            summary = "부모 일일 복약 일정 조회 API",
            description = "자녀 사용자가 같은 가족에 속한 부모님의 특정 날짜 복약 일정을 예정 시간 오름차순으로 조회합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "부모 복약 일정 조회 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "날짜 누락 또는 잘못된 날짜 형식",
                    content = @Content(
                            schema = @Schema(
                                    implementation = Response.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "해당 부모의 복약 일정을 조회할 권한이 없음",
                    content = @Content(
                            schema = @Schema(
                                    implementation = Response.class
                            )
                    )
            )
    })
    public Response<List<MedicationScheduleResponse>> getParentDailyMedicationSchedules(
            @Parameter(hidden = true)
            @AuthenticationPrincipal User user,

            @Parameter(
                    description = "조회 대상 부모 사용자 ID",
                    example = "2",
                    required = true
            )
            @PathVariable("parentUserId")
            Long parentUserId,

            @Parameter(
                    description = "조회할 날짜",
                    required = true,
                    schema = @Schema(
                            type = "string",
                            format = "date",
                            example = "2026-07-25"
                    )
            )
            @RequestParam("date")
            @NotNull(message = "날짜는 필수 입력값입니다.")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date
    ) {
        List<MedicationScheduleResponse> responses =
                medicationLogService.getDailyMedicationSchedules(
                        user.getUsersId(),
                        parentUserId,
                        date
                );

        return Response.ok(responses);
    }

    @PatchMapping("/medication-logs/{medicationLogId}/check")
    @Operation(
            summary = "복약 체크",
            description = "복약 완료 상태로 변경하고 관련 알림을 처리합니다."
    )
    public Response<MedicationCheckResponse> checkMedication(
            @Parameter(hidden = true)
            @AuthenticationPrincipal User user,

            @Parameter(
                    description = "복약 로그 ID",
                    example = "1",
                    required = true
            )
            @PathVariable("medicationLogId")
            Long medicationLogId
    ) {
        MedicationCheckResponse response =
                medicationLogService.checkMedication(
                        medicationLogId,
                        user
                );

        return Response.ok(
                ResultCode.OK,
                response
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(
                        Response.fail(
                                ErrorCode.BAD_REQUEST
                        )
                );
    }
}