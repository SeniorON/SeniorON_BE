package com.example.senioron.domain.user.controller;

import com.example.senioron.domain.user.dto.kakao.request.KakaoLoginRequest;
import com.example.senioron.domain.user.dto.kakao.response.KakaoLoginResponse;
import com.example.senioron.domain.user.dto.request.UserLoginRequest;
import com.example.senioron.domain.user.dto.request.UserRoleUpdateRequest;
import com.example.senioron.domain.user.dto.request.UserSignUpRequest;
import com.example.senioron.domain.user.dto.response.*;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.service.KakaoLoginService;
import com.example.senioron.domain.user.service.UserService;
import com.example.senioron.global.apiPayload.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "회원", description = "회원가입, 로그인, 계정 관련 API")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final KakaoLoginService kakaoLoginService;

    @Operation(summary = "아이디 중복 확인", description = "회원가입 시 입력한 아이디가 이미 사용 중인지 확인합니다.")
    @GetMapping("/check-login-id")
    public Response<LoginIdCheckResponse> checkLoginId(@RequestParam String loginId) {
        return Response.ok(userService.checkLoginId(loginId));
    }

    @Operation(summary = "회원가입", description = "이메일 인증 후 아이디, 비밀번호, 약관 동의 정보를 바탕으로 회원가입을 진행합니다.")
    @PostMapping("/signup")
    public Response<UserSignUpResponse> signUp(@Valid @RequestBody UserSignUpRequest request) {
        return Response.ok(userService.signUp(request));
    }

    @Operation(summary = "로그인", description = "아이디와 비밀번호로 로그인하고 JWT accessToken을 발급합니다.")
    @PostMapping("/login")
    public Response<UserLoginResponse> login(@Valid @RequestBody UserLoginRequest request) {
        return Response.ok(userService.login(request));
    }


    @Operation(summary = "역할 선택/변경", description = "현재 로그인한 사용자의 역할을 선택하거나 변경합니다.")
    @PatchMapping("/me/role")
    public Response<UserRoleUpdateResponse> updateRole(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody UserRoleUpdateRequest request
    ) {
        return Response.ok(userService.updateRole(user, request));
    }

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
}