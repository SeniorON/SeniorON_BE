package com.example.senioron.domain.family.controller;

import com.example.senioron.domain.family.dto.request.FamilyPhotoCreateRequest;
import com.example.senioron.domain.family.dto.response.FamilyPhotoAlbumResponse;
import com.example.senioron.domain.family.dto.response.FamilyPhotoItemResponse;
import com.example.senioron.domain.family.dto.response.FamilyPhotoListResponse;
import com.example.senioron.domain.family.service.FamilyPhotoService;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.global.apiPayload.code.ResultCode;
import com.example.senioron.global.apiPayload.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "가족 사진", description = "가족 사진 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/family/photos")
public class FamilyPhotoController {

    private final FamilyPhotoService familyPhotoService;

    @Operation(summary = "가족 사진 등록", description = "현재 로그인한 사용자의 가족에 사진을 등록합니다.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Response<FamilyPhotoItemResponse> createPhoto(
            @AuthenticationPrincipal User user,
            @Valid @ModelAttribute FamilyPhotoCreateRequest request
    ) {
        return Response.ok(familyPhotoService.createPhoto(user, request));
    }

    @Operation(summary = "가족 사진 목록 조회", description = "현재 사용자가 속한 가족의 사진을 최신순으로 조회합니다. " + "uploaderUserId를 전달하면 해당 자녀의 사진만 조회합니다.")
    @GetMapping
    public Response<FamilyPhotoListResponse> getPhotos(
            @AuthenticationPrincipal User user,
            @RequestParam(name = "uploaderUserId", required = false) Long uploaderUserId,
            @RequestParam(name = "cursorCreatedAt", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime cursorCreatedAt,
            @RequestParam(name = "cursorId", required = false) Long cursorId,
            @RequestParam(name = "size", defaultValue = "10") int size
    ) {
        return Response.ok(
                familyPhotoService.getPhotos(user, uploaderUserId, cursorCreatedAt, cursorId, size)
        );
    }

    @Operation(summary = "가족 사진 삭제", description = "본인이 등록한 사진을 삭제합니다." + "업로더가 가족에서 나간 경우 주 담당자가 삭제할 수 있습니다.")
    @DeleteMapping("/{familyPhotoId}")
    public Response<Void> deletePhoto(
            @AuthenticationPrincipal User user,
            @PathVariable("familyPhotoId") Long familyPhotoId
    ) {
        familyPhotoService.deletePhoto(user, familyPhotoId);

        return Response.ok(ResultCode.OK, null);
    }

    @Operation(summary = "자녀별 가족 사진 앨범 조회", description = "부모가 같은 가족 자녀의 최신 사진과 전체 사진 수, 새로운 사진 여부를 조회합니다.")
    @GetMapping("/albums")
    public Response<List<FamilyPhotoAlbumResponse>> getPhotoAlbums(
            @AuthenticationPrincipal User user
    ){
        return Response.ok(
                familyPhotoService.getPhotoAlbums(user)
        );
    }

    @Operation(summary = "가족 사진 확인 처리", description = "부모가 사진 상세 화면을 열었을 때 해당 사진 한 장을 확인 처리합니다.")
    @PatchMapping("/{familyPhotoId}/viewed")
    public Response<Void> markPhotoAsViewed(
            @AuthenticationPrincipal User user,
            @PathVariable("familyPhotoId") Long familyPhotoId
    ) {
        familyPhotoService.markPhotoAsViewed(
                user,
                familyPhotoId
        );
        return Response.ok();
    }
}
