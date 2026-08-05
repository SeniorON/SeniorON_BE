package com.example.senioron.domain.socialaccount.controller;

import com.example.senioron.domain.socialaccount.dto.google.request.GoogleLoginRequest;
import com.example.senioron.domain.socialaccount.dto.google.response.GoogleLoginResponse;
import com.example.senioron.domain.socialaccount.dto.request.SocialSignupRequest;
import com.example.senioron.domain.socialaccount.dto.response.SocialSignupResponse;
import com.example.senioron.domain.socialaccount.dto.kakao.request.KakaoLoginRequest;
import com.example.senioron.domain.socialaccount.dto.kakao.response.KakaoLoginResponse;
import com.example.senioron.domain.socialaccount.service.GoogleLoginService;
import com.example.senioron.domain.socialaccount.service.KakaoLoginService;
import com.example.senioron.domain.socialaccount.service.SocialSignupService;
import com.example.senioron.global.apiPayload.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "소셜 로그인", description = "카카오, 구글 로그인 관련 API")
@RestController
@RequestMapping("/api/social-accounts")
@RequiredArgsConstructor
public class SocialAccountController {

    private final KakaoLoginService kakaoLoginService;
    private final GoogleLoginService googleLoginService;
    private final SocialSignupService socialSignupService;

    @Operation(
            summary = "카카오 로그인",
            description = "안드로이드에서 발급받은 카카오 액세스 토큰으로 로그인 또는 회원가입을 진행합니다."
    )
    @PostMapping("/login/kakao")
    public Response<KakaoLoginResponse> kakaoLogin(
            @Valid @RequestBody KakaoLoginRequest request
    ) {
        return Response.ok(
                kakaoLoginService.kakaoLogin(request)
        );
    }

    @Operation(
            summary = "구글 로그인",
            description = "Firebase Authentication에서 발급받은 ID 토큰으로 구글 로그인을 진행합니다."
    )
    @PostMapping("/login/google")
    public Response<GoogleLoginResponse> googleLogin(
            @Valid @RequestBody GoogleLoginRequest request
    ) {
        return Response.ok(googleLoginService.googleLogin(request));
    }

    @Operation(
            summary = "소셜 회원가입 완료",
            description = "신규 소셜 사용자의 추가 정보를 받아 회원가입을 완료하고 JWT를 발급합니다. fcmToken과 deviceIdentifier를 함께 보내면 기기를 등록하고 refresh token을 해당 기기에 연결합니다."
    )
    @PostMapping("/signup")
    public Response<SocialSignupResponse> socialSignup(
            @Valid @RequestBody SocialSignupRequest request
    ) {
        return Response.ok(socialSignupService.signup(request));
    }


    @Operation(
            summary = "카카오 callback",
            description = "call 테스트를 위해 넣음"
    )
    @GetMapping("/login/kakao/callback")
    public ResponseEntity<String> kakaoCallback(
            @RequestParam String code
    ) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(code);
    }
}
