package com.example.senioron.domain.hospital.controller;

import com.example.senioron.domain.hospital.dto.request.HospitalCreateRequest;
import com.example.senioron.domain.hospital.dto.request.HospitalUpdateRequest;
import com.example.senioron.domain.hospital.dto.response.HospitalCreateResponse;
import com.example.senioron.domain.hospital.dto.response.HospitalDetailResponse;
import com.example.senioron.domain.hospital.dto.response.HospitalListResponse;
import com.example.senioron.domain.hospital.dto.response.HospitalUpcomingResponse;
import com.example.senioron.domain.hospital.service.HospitalService;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.code.ResultCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import com.example.senioron.global.apiPayload.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(
        name = "병원",
        description = "병원 일정 API"
)
@RequestMapping("/api/hospitals")
public class HospitalController {

    private final HospitalService hospitalService;

    @Operation(
            summary = "병원 일정 등록",
            description = "로그인한 자녀가 같은 가족에 속한 시니어의 병원 진료 일정을 등록합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "병원 일정 등록 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 날짜 또는 시간 형식"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "병원 일정 등록 권한이 없음"
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
    public Response<HospitalCreateResponse> createHospital(
            @AuthenticationPrincipal
            User user,

            @Parameter(
                    description = "병원 일정을 등록할 시니어 ID",
                    required = true,
                    example = "1"
            )
            @PathVariable
            Long seniorId,

            @Valid
            @RequestBody
            HospitalCreateRequest request
    ) {
        HospitalCreateResponse result =
                hospitalService.createHospital(
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
            summary = "병원 일정 삭제",
            description = "로그인한 자녀가 같은 가족에 속한 시니어의 특정 병원 진료 일정을 삭제합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "병원 일정 삭제 성공"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "병원 일정 삭제 권한이 없음"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "시니어, 부모 계정, 가족 구성원 또는 병원 일정을 찾을 수 없음",
                    content = @Content(
                            schema = @Schema(
                                    implementation = Response.class
                            )
                    )
            )
    })
    @DeleteMapping(
            "/seniors/{seniorId}/{hospitalId}"
    )
    @ResponseStatus(HttpStatus.OK)
    public Response<Void> deleteHospital(
            @AuthenticationPrincipal
            User user,

            @Parameter(
                    description = "병원 일정의 대상 시니어 ID",
                    required = true,
                    example = "1"
            )
            @PathVariable
            Long seniorId,

            @Parameter(
                    description = "삭제할 병원 일정 ID",
                    required = true,
                    example = "1"
            )
            @PathVariable
            Long hospitalId
    ) {
        hospitalService.deleteHospital(
                user.getUsersId(),
                seniorId,
                hospitalId
        );

        return Response.ok(
                ResultCode.OK,
                null
        );
    }

    @Operation(
            summary = "시니어 병원 일정 월별 목록 조회",
            description = "특정 시니어의 연도와 월에 해당하는 병원 일정을 날짜순으로 조회합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "병원 일정 월별 조회 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "유효하지 않은 월"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "병원 일정 조회 권한이 없음"
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
    public Response<List<HospitalListResponse>>
    getHospitalByMonth(
            @AuthenticationPrincipal
            User user,

            @Parameter(
                    description = "조회 대상 시니어 ID",
                    required = true,
                    example = "1"
            )
            @PathVariable
            Long seniorId,

            @RequestParam
            int year,

            @RequestParam
            int month
    ) {
        if (month < 1 || month > 12) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST
            );
        }

        List<HospitalListResponse> responses =
                hospitalService.getHospitalByMonth(
                        user.getUsersId(),
                        seniorId,
                        year,
                        month
                );

        return Response.ok(
                ResultCode.OK,
                responses
        );
    }

    @Operation(
            summary = "병원 일정 수정",
            description = "로그인한 자녀가 같은 가족에 속한 시니어의 특정 병원 진료 일정을 수정합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "병원 일정 수정 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 날짜 또는 시간 형식"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "병원 일정 수정 권한이 없음"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "시니어, 부모 계정, 가족 구성원 또는 병원 일정을 찾을 수 없음",
                    content = @Content(
                            schema = @Schema(
                                    implementation = Response.class
                            )
                    )
            )
    })
    @PutMapping(
            "/seniors/{seniorId}/{hospitalId}"
    )
    @ResponseStatus(HttpStatus.OK)
    public Response<Void> updateHospital(
            @AuthenticationPrincipal
            User user,

            @Parameter(
                    description = "병원 일정의 대상 시니어 ID",
                    required = true,
                    example = "1"
            )
            @PathVariable
            Long seniorId,

            @Parameter(
                    description = "수정할 병원 일정 ID",
                    required = true,
                    example = "1"
            )
            @PathVariable
            Long hospitalId,

            @Valid
            @RequestBody
            HospitalUpdateRequest request
    ) {
        hospitalService.updateHospital(
                user.getUsersId(),
                seniorId,
                hospitalId,
                request
        );

        return Response.ok(
                ResultCode.OK,
                null
        );
    }

    @Operation(
            summary = "특정 날짜 진료 상세 조회",
            description = "특정 시니어의 지정된 날짜에 해당하는 진료 상세 정보를 조회합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "특정 날짜 병원 일정 조회 성공"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "병원 일정 조회 권한이 없음"
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
    @GetMapping(
            "/seniors/{seniorId}/daily"
    )
    @ResponseStatus(HttpStatus.OK)
    public Response<List<HospitalDetailResponse>>
    getHospitalByDate(
            @AuthenticationPrincipal
            User user,

            @Parameter(
                    description = "조회 대상 시니어 ID",
                    required = true,
                    example = "1"
            )
            @PathVariable
            Long seniorId,

            @RequestParam
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE
            )
            @Parameter(
                    description = "조회할 날짜",
                    example = "2026-06-19"
            )
            LocalDate date
    ) {
        List<HospitalDetailResponse> responses =
                hospitalService.getHospitalByDate(
                        user.getUsersId(),
                        seniorId,
                        date
                );

        return Response.ok(
                ResultCode.OK,
                responses
        );
    }

    @Operation(
            summary = "다가오는 병원 일정 조회",
            description = "오늘 이후 병원 일정을 날짜와 시간순으로 조회하여 가장 가까운 날짜 카드 최대 2개를 반환합니다. 같은 날짜의 진료 일정은 모두 반환합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "다가오는 병원 일정 조회 성공"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "병원 일정 조회 권한이 없음",
                    content = @Content(
                            schema = @Schema(
                                    implementation = Response.class
                            )
                    )
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
    @GetMapping(
            "/seniors/{seniorId}/upcoming"
    )
    @ResponseStatus(HttpStatus.OK)
    public Response<List<HospitalUpcomingResponse>>
    getUpcomingHospitals(
            @AuthenticationPrincipal
            User user,

            @Parameter(
                    description = "조회 대상 시니어 ID",
                    required = true,
                    example = "1"
            )
            @PathVariable
            Long seniorId
    ) {
        List<HospitalUpcomingResponse> responses =
                hospitalService.getUpcomingHospitals(
                        user.getUsersId(),
                        seniorId
                );

        return Response.ok(
                ResultCode.OK,
                responses
        );
    }
}