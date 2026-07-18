package com.example.senioron.domain.hospital.controller;

import com.example.senioron.domain.hospital.dto.request.HospitalUpdateRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.example.senioron.domain.hospital.dto.request.HospitalCreateRequest;
import com.example.senioron.domain.hospital.dto.response.HospitalCreateResponse;
import com.example.senioron.domain.hospital.dto.response.HospitalListResponse;
import com.example.senioron.domain.hospital.service.HospitalService;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.global.apiPayload.code.ResultCode;
import com.example.senioron.global.apiPayload.response.Response;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "병원", description = "병원 일정 API")
@RequestMapping("/api/hospitals")
public class HospitalController {

    private final HospitalService hospitalService;

    @Operation(
            summary = "병원 일정 등록",
            description = "로그인한 사용자가 부모님의 병원 진료 일정을 등록합니다."
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response<HospitalCreateResponse> createHospital(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody HospitalCreateRequest request
    ) {
        HospitalCreateResponse result =
                hospitalService.createHospital(user, request);

        return Response.ok(ResultCode.CREATED, result);
    }

    @Operation(
            summary = "병원 일정 삭제",
            description = "등록된 특정 병원 진료 일정을 삭제합니다."
    )
    @DeleteMapping("/{hospitalId}")
    @ResponseStatus(HttpStatus.OK)
    public Response<Void> deleteHospital(
            @AuthenticationPrincipal User user,
            @PathVariable("hospitalId") Long hospitalId
    ) {
        hospitalService.deleteHospital(user, hospitalId);
        return Response.ok(ResultCode.OK, null);
    }

    @Operation(
            summary = "부모님 병원 일정 월별 목록 조회",
            description = "특정 연도(year)와 월(month)에 해당하는 병원 일정을 날짜순으로 조회합니다."
    )
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public Response<List<HospitalListResponse>> getHospitalByMonth(
                                                                    @AuthenticationPrincipal User user,
                                                                    @RequestParam(value = "year") int year,
                                                                    @RequestParam(value = "month") int month
    ) {
        if (month < 1 || month > 12) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        List<HospitalListResponse> responses = hospitalService.getHospitalByMonth(user, year, month);
        return Response.ok(ResultCode.OK, responses);
    }
    @Operation(
            summary = "병원 일정 수정",
            description = "등록된 특정 병원 진료 일정을 수정합니다."
    )
    @PutMapping("/{hospitalId}")
    @ResponseStatus(HttpStatus.OK)
    public Response<Void> updateHospital(
            @AuthenticationPrincipal User user,
            @PathVariable("hospitalId") Long hospitalId,
            @Valid @RequestBody HospitalUpdateRequest request
    ) {
        hospitalService.updateHospital(user, hospitalId, request);
        return Response.ok(ResultCode.OK, null);
    }

}