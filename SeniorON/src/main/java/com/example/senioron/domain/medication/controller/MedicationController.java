package com.example.senioron.domain.medication.controller;

import com.example.senioron.domain.medication.dto.request.MedicationCreateRequest;
import com.example.senioron.domain.medication.dto.response.MedicationCreateResponse;
import com.example.senioron.domain.medication.dto.response.MedicationReadResponse;
import com.example.senioron.domain.medication.service.MedicationService;
import com.example.senioron.global.apiPayload.code.ResultCode;
import com.example.senioron.global.apiPayload.response.Response;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/medications")
public class MedicationController {

    private final MedicationService medicationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response<MedicationCreateResponse> createMedication(
            @RequestParam Long userId,
            @Valid @RequestBody MedicationCreateRequest request
    ) {
        MedicationCreateResponse result =
                medicationService.createMedication(userId, request);

        return Response.ok(ResultCode.CREATED, result);
    }

    @GetMapping
    public Response<List<MedicationReadResponse>> getMedications(
            @RequestParam Long userId
    ) {
        List<MedicationReadResponse> result =
                medicationService.getMedications(userId);

        return Response.ok(result);
    }
}