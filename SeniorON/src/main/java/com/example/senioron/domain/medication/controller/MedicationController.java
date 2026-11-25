package com.example.senioron.domain.medication.controller;

import com.example.senioron.domain.medication.dto.request.MedicationCreateRequest;
import com.example.senioron.domain.medication.dto.request.MedicationUpdateRequest;
import com.example.senioron.domain.medication.dto.response.MedicationCreateResponse;
import com.example.senioron.domain.medication.dto.response.MedicationReadResponse;
import com.example.senioron.domain.medication.service.MedicationService;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.global.apiPayload.code.ResultCode;
import com.example.senioron.global.apiPayload.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(
        name = "약",
        description = "복약 관리 API"
)
@RequestMapping("/api/medications")
public class MedicationController {

    private final MedicationService medicationService;

    @Operation(
            summary = "약 등록",
            description = "자녀가 같은 가족에 속한 시니어의 복약 정보와 반복 일정을 등록합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "약 등록 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "복약 일정 또는 반복 설정 값이 올바르지 않음"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "약 등록 권한이 없음"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "시니어, 부모 계정 또는 가족 구성원을 찾을 수 없음",
                    content = @Content(
                            schema = @Schema(
                                    implementation = Response.class
                            )
                    )
            )
    })
    @PostMapping("/seniors/{seniorId}")
    @ResponseStatus(HttpStatus.CREATED)
    public Response<MedicationCreateResponse> createMedication(
            @Parameter(hidden = true)
            @AuthenticationPrincipal
            User user,

            @Parameter(
                    description = "약을 등록할 시니어 ID",
                    example = "1",
                    required = true
            )
            @PathVariable("seniorId")
            Long seniorId,

            @Valid
            @RequestBody
            MedicationCreateRequest request
    ) {
        MedicationCreateResponse result =
                medicationService.createMedication(
                        user.getUsersId(),
                        seniorId,
                        request
                );

        return Response.ok(
                ResultCode.CREATED,
                result
        );
    }

    @Operation(
            summary = "약 목록 조회",
            description = "자녀는 같은 가족에 속한 시니어의 복약 정보를 조회하고, 부모는 본인과 연결된 시니어의 복약 정보만 조회합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "약 목록 조회 성공"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "복약 정보 조회 권한이 없음"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "시니어, 부모 계정 또는 가족 구성원을 찾을 수 없음",
                    content = @Content(
                            schema = @Schema(
                                    implementation = Response.class
                            )
                    )
            )
    })
    @GetMapping("/seniors/{seniorId}")
    @ResponseStatus(HttpStatus.OK)
    public Response<List<MedicationReadResponse>> getMedications(
            @Parameter(hidden = true)
            @AuthenticationPrincipal
            User user,

            @Parameter(
                    description = "조회 대상 시니어 ID",
                    example = "1",
                    required = true
            )
            @PathVariable("seniorId")
            Long seniorId
    ) {
        List<MedicationReadResponse> result =
                medicationService.getMedications(
                        user.getUsersId(),
                        seniorId
                );

        return Response.ok(
                result
        );
    }

    @Operation(
            summary = "약 수정",
            description = "자녀가 같은 가족에 속한 시니어의 약 그룹을 수정하고 변경된 반복 규칙과 복용 기간에 따라 복약 로그를 다시 생성합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "약 수정 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "복약 일정 또는 반복 설정 값이 올바르지 않음"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "약 수정 권한이 없음"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "시니어, 부모 계정, 가족 구성원 또는 복약 정보를 찾을 수 없음",
                    content = @Content(
                            schema = @Schema(
                                    implementation = Response.class
                            )
                    )
            )
    })
    @PutMapping("/seniors/{seniorId}")
    @ResponseStatus(HttpStatus.OK)
    public Response<String> updateMedication(
            @Parameter(hidden = true)
            @AuthenticationPrincipal
            User user,

            @Parameter(
                    description = "약을 수정할 시니어 ID",
                    example = "1",
                    required = true
            )
            @PathVariable("seniorId")
            Long seniorId,

            @Valid
            @RequestBody
            MedicationUpdateRequest request
    ) {
        medicationService.updateMedication(
                user.getUsersId(),
                seniorId,
                request
        );

        return Response.ok(
                "약 일정이 성공적으로 수정되었습니다."
        );
    }

    @Operation(
            summary = "약 삭제",
            description = "자녀가 같은 가족에 속한 시니어의 약 그룹과 관련 복약 로그를 삭제합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "약 삭제 성공"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "약 삭제 권한이 없음"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "시니어, 부모 계정, 가족 구성원 또는 복약 정보를 찾을 수 없음",
                    content = @Content(
                            schema = @Schema(
                                    implementation = Response.class
                            )
                    )
            )
    })
    @DeleteMapping(
            "/seniors/{seniorId}/groups/{medicationGroupId}"
    )
    @ResponseStatus(HttpStatus.OK)
    public Response<String> deleteMedicationGroup(
            @Parameter(hidden = true)
            @AuthenticationPrincipal
            User user,

            @Parameter(
                    description = "약을 삭제할 시니어 ID",
                    example = "1",
                    required = true
            )
            @PathVariable("seniorId")
            Long seniorId,

            @Parameter(
                    description = "삭제할 약 그룹 ID",
                    example = "f2b47b9d-962d-430b-b328-d25ed693acaf",
                    required = true
            )
            @PathVariable("medicationGroupId")
            String medicationGroupId
    ) {
        medicationService.deleteMedicationGroup(
                user.getUsersId(),
                seniorId,
                medicationGroupId
        );

        return Response.ok(
                "약 일정이 성공적으로 삭제되었습니다."
        );
    }
}