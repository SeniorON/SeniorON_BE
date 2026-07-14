package com.example.senioron.domain.socialaccount.controller;

import com.example.senioron.domain.socialaccount.dto.kakao.request.KakaoLoginRequest;
import com.example.senioron.domain.socialaccount.dto.kakao.response.KakaoLoginResponse;
import com.example.senioron.domain.socialaccount.service.KakaoLoginService;
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


    @GetMapping("/login/kakao/callback")
    public ResponseEntity<String> kakaoCallback(
            @RequestParam String code
    ) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(code);
    }
}
