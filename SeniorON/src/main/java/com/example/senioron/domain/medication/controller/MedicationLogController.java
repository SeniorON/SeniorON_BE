package com.example.senioron.domain.medication.controller;

import com.example.senioron.domain.medication.dto.response.MedicationCheckResponse;
import com.example.senioron.domain.medication.service.MedicationLogService;
import com.example.senioron.domain.user.entity.User;

import com.example.senioron.global.apiPayload.code.ResultCode;
import com.example.senioron.global.apiPayload.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@Tag(name = "Medication Log API", description = "복약 기록 및 체크 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/medication-logs")
public class MedicationLogController {

    private final MedicationLogService medicationLogService;

    @Operation(summary = "복약 체크 (상태 변경)", description = "복약 완료 상태로 변경하고 관련 알림을 처리합니다.")
    @PatchMapping("/{medicationLogId}/check")
    public Response<MedicationCheckResponse> checkMedication(
            @AuthenticationPrincipal User user,
            @PathVariable("medicationLogId") Long medicationLogId
    ) {
        MedicationCheckResponse response = medicationLogService.checkMedication(medicationLogId, user);
        return Response.ok(ResultCode.OK, response);
    }
}