package com.example.senioron.domain.hospital.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import com.example.senioron.domain.hospital.dto.request.HospitalCreateRequest;
import com.example.senioron.domain.hospital.dto.response.HospitalCreateResponse;
import com.example.senioron.domain.hospital.service.HospitalService;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.global.apiPayload.code.ResultCode;
import com.example.senioron.global.apiPayload.response.Response;
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
}