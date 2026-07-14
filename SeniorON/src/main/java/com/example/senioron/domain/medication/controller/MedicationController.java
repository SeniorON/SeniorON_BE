package com.example.senioron.domain.medication.controller;

import com.example.senioron.domain.medication.dto.request.MedicationCreateRequest;
import com.example.senioron.domain.medication.dto.response.MedicationCreateResponse;
import com.example.senioron.domain.medication.dto.response.MedicationReadResponse;
import com.example.senioron.domain.medication.service.MedicationService;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.global.apiPayload.code.ResultCode;
import com.example.senioron.global.apiPayload.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "약", description = "복약 관리 API")
@RequestMapping("/api/medications")
public class MedicationController {

    private final MedicationService medicationService;

    @Operation(
            summary = "약 등록",
            description = "로그인한 사용자가 부모님의 복약 정보를 등록합니다."
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response<MedicationCreateResponse> createMedication(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody MedicationCreateRequest request
    ) {
        MedicationCreateResponse result =
                medicationService.createMedication(user.getUsersId(), request);

        return Response.ok(ResultCode.CREATED, result);
    }

    @Operation(
            summary = "약 목록 조회",
            description = "로그인한 사용자가 부모님의 복약 정보를 조회합니다."
    )
    @GetMapping
    public Response<List<MedicationReadResponse>> getMedications(
            @AuthenticationPrincipal User user
    ) {
        List<MedicationReadResponse> result =
                medicationService.getMedications(user.getUsersId());

        return Response.ok(result);
    }

    @Operation(
            summary = "약 삭제",
            description = "그룹 ID를 기준으로 복약 정보를 일괄 삭제합니다."
    )
    @DeleteMapping("/groups/{medicationGroupId}")
    public Response<String> deleteMedicationGroup(
            @AuthenticationPrincipal User user, // 💡 요렇게 User 객체로 바로 받기!
            @PathVariable("medicationGroupId") String medicationGroupId
    ) {
        medicationService.deleteMedicationGroup(user.getUsersId(), medicationGroupId);
        return Response.ok("약 일정이 성공적으로 삭제되었습니다.");
    }
}