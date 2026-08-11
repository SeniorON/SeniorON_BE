package com.example.senioron.domain.user.controller;

import com.example.senioron.domain.user.dto.request.NameUpdateRequest;
import com.example.senioron.domain.user.dto.request.PasswordChangeRequest;
import com.example.senioron.domain.user.dto.response.NameUpdateResponse;
import com.example.senioron.domain.user.dto.response.PasswordChangeResponse;
import com.example.senioron.domain.user.dto.response.ProfileImageResponse;
import com.example.senioron.domain.user.dto.response.ProfileImageUpdateResponse;
import com.example.senioron.domain.user.dto.response.UserAccountResponse;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.service.UserSettingsService;
import com.example.senioron.global.apiPayload.response.Response;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "회원 설정", description = "로그인 사용자의 계정 설정 관련 API")
@RestController
@RequestMapping("/api/users/settings")
@RequiredArgsConstructor
public class UserSettingsController {

    private final UserSettingsService userSettingsService;

    @Operation(summary = "내 계정 정보 조회", description = "현재 로그인한 사용자의 이름, 역할, 이메일을 조회합니다.")
    @GetMapping("/account")
    public Response<UserAccountResponse> getAccount(
            @AuthenticationPrincipal User user
    ) {
        return Response.ok(userSettingsService.getAccount(user));
    }

    @Operation(summary = "이름 변경", description = "로그인한 사용자의 이름을 새로운 이름으로 변경합니다.")
    @PatchMapping("/name")
    public Response<NameUpdateResponse> updateName(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody NameUpdateRequest request
    ) {
        return Response.ok(userSettingsService.updateName(user, request));
    }

    @Operation(
            summary = "비밀번호 변경",
            description = "로그인한 사용자가 현재 비밀번호를 확인한 뒤 새 비밀번호로 변경합니다."
    )
    @PatchMapping("/password")
    public Response<PasswordChangeResponse> changePassword(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody PasswordChangeRequest request
    ) {
        return Response.ok(userSettingsService.changePassword(user, request));
    }

    @Operation(
            summary = "프로필 이미지 변경",
            description = "로그인한 사용자의 프로필 이미지를 새로운 이미지로 변경합니다."
    )
    @PatchMapping(value = "/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Response<ProfileImageUpdateResponse> updateProfileImage(
            @AuthenticationPrincipal User user,
            @Parameter(
                    description = "변경할 프로필 이미지",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                            schema = @Schema(type = "string", format = "binary")
                    )
            )
            @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        return Response.ok(userSettingsService.updateProfileImage(user, image));
    }

    @Operation(
            summary = "프로필 이미지 조회",
            description = "로그인한 사용자의 프로필 이미지 URL을 조회합니다."
    )
    @GetMapping("/profile-image")
    public Response<ProfileImageResponse> getProfileImage(
            @AuthenticationPrincipal User user
    ) {
        return Response.ok(userSettingsService.getProfileImage(user));
    }
}
