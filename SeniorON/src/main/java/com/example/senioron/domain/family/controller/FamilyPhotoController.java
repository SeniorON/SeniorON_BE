package com.example.senioron.domain.family.controller;

import com.example.senioron.domain.family.dto.request.FamilyPhotoCreateRequest;
import com.example.senioron.domain.family.dto.response.FamilyPhotoCreateResponse;
import com.example.senioron.domain.family.service.FamilyPhotoService;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.global.apiPayload.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
