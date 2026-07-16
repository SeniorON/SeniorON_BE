package com.example.senioron.domain.family.controller;

import com.example.senioron.domain.family.dto.request.FamilyPhotoCreateRequest;
import com.example.senioron.domain.family.dto.response.FamilyPhotoCreateResponse;
import com.example.senioron.domain.family.dto.response.FamilyPhotoListResponse;
import com.example.senioron.domain.family.service.FamilyPhotoService;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.global.apiPayload.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "가족 사진", description = "가족 사진 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/family/photos")
public class FamilyPhotoController {

    private final FamilyPhotoService familyPhotoService;

    @Operation(summary = "가족 사진 등록", description = "현재 로그인한 사용자의 가족에 사진을 등록합니다.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Response<FamilyPhotoCreateResponse> createPhoto(
            @AuthenticationPrincipal User user,
            @Valid @ModelAttribute FamilyPhotoCreateRequest request
    ) {
        return Response.ok(familyPhotoService.createPhoto(user, request));
    }

    @Operation(summary = "가족 사진 목록 조회", description = "현재 사용자가 속한 가족의 사진을 최신순으로 조회합니다.")
    @GetMapping
    public Response<FamilyPhotoListResponse> getPhotos(
            @AuthenticationPrincipal User user,
            @RequestParam(name = "cursor", required = false) Long cursor,
            @RequestParam(name = "size", defaultValue = "10") int size
    ) {
        return Response.ok(
                familyPhotoService.getPhotos(user, cursor, size)
        );
    }
}
