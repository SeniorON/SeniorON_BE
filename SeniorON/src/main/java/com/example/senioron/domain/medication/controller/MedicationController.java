package com.example.senioron.domain.medication.controller;

import com.example.senioron.domain.medication.dto.request.MedicationCreateRequest;
import com.example.senioron.domain.medication.dto.request.MedicationUpdateRequest;
import com.example.senioron.domain.medication.dto.response.MedicationCreateResponse;
import com.example.senioron.domain.medication.dto.response.MedicationReadResponse;
import com.example.senioron.domain.medication.service.MedicationService;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.global.apiPayload.code.ResultCode;
import com.example.senioron.global.apiPayload.response.Response;
import io.swagger.v3.oas.annotations.Parameter;
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
            description = "주담당자 또는 보조담당자 자녀가 같은 가족에 속한 부모님의 복약 정보를 등록합니다."
    )
    @PostMapping("/parents/{parentUserId}")
    @ResponseStatus(HttpStatus.CREATED)
    public Response<MedicationCreateResponse> createMedication(
            @Parameter(hidden = true)
            @AuthenticationPrincipal User user,
            @Parameter(
                    description = "약을 등록할 부모 사용자 ID",
                    example = "2",
                    required = true
            )
            @PathVariable("parentUserId") Long parentUserId,
            @Valid @RequestBody MedicationCreateRequest request
    ) {
        MedicationCreateResponse result = medicationService.createMedication(
                user.getUsersId(),
                parentUserId,
                request
        );

        return Response.ok(ResultCode.CREATED, result);
    }

    @Operation(
            summary = "약 목록 조회",
            description = "자녀는 같은 가족 부모님의 복약 정보를 조회하고, 부모는 본인의 복약 정보만 조회합니다."
    )
    @GetMapping("/parents/{parentUserId}")
    public Response<List<MedicationReadResponse>> getMedications(
            @Parameter(hidden = true)
            @AuthenticationPrincipal User user,
            @Parameter(
                    description = "조회할 부모 사용자 ID. 부모 계정은 본인의 사용자 ID만 사용할 수 있습니다.",
                    example = "2",
                    required = true
            )
            @PathVariable("parentUserId") Long parentUserId
    ) {
        List<MedicationReadResponse> result = medicationService.getMedications(
                user.getUsersId(),
                parentUserId
        );

        return Response.ok(result);
    }
    @Operation(
            summary = "약 수정",
            description = "주담당자 또는 보조담당자 자녀가 약 그룹을 수정하고 변경된 정보에 따라 30일치 복약 로그를 다시 생성합니다."
    )
    @PutMapping("/parents/{parentUserId}")
    public Response<String> updateMedication(
            @Parameter(hidden = true)
            @AuthenticationPrincipal User user,
            @Parameter(
                    description = "약을 수정할 부모 사용자 ID",
                    example = "2",
                    required = true
            )
            @PathVariable("parentUserId") Long parentUserId,
            @Valid @RequestBody MedicationUpdateRequest request
    ) {
        medicationService.updateMedication(
                user.getUsersId(),
                parentUserId,
                request
        );

        return Response.ok("약 일정이 성공적으로 수정되었습니다.");
    }

    @Operation(
            summary = "약 삭제",
            description = "주담당자 또는 보조담당자 자녀가 같은 가족 부모님의 약 그룹과 관련 복약 로그를 삭제합니다."
    )
    @DeleteMapping("/parents/{parentUserId}/groups/{medicationGroupId}")
    public Response<String> deleteMedicationGroup(
            @Parameter(hidden = true)
            @AuthenticationPrincipal User user,
            @Parameter(
                    description = "약을 삭제할 부모 사용자 ID",
                    example = "2",
                    required = true
            )
            @PathVariable("parentUserId") Long parentUserId,
            @Parameter(
                    description = "삭제할 약 그룹 ID",
                    example = "f2b47b9d-962d-430b-b328-d25ed693acaf",
                    required = true
            )
            @PathVariable("medicationGroupId") String medicationGroupId
    ) {
        medicationService.deleteMedicationGroup(
                user.getUsersId(),
                parentUserId,
                medicationGroupId
        );

        return Response.ok("약 일정이 성공적으로 삭제되었습니다.");
    }


}