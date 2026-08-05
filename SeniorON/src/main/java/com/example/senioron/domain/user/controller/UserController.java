package com.example.senioron.domain.user.controller;

import com.example.senioron.domain.user.dto.request.SignupEmailVerificationCodeSendRequest;
import com.example.senioron.domain.user.dto.request.SignupEmailVerificationCodeVerifyRequest;
import com.example.senioron.domain.user.dto.request.TokenRefreshRequest;
import com.example.senioron.domain.user.dto.request.UserLoginRequest;
import com.example.senioron.domain.user.dto.request.UserLogoutRequest;
import com.example.senioron.domain.user.dto.request.UserRoleUpdateRequest;
import com.example.senioron.domain.user.dto.request.UserSignUpRequest;
import com.example.senioron.domain.user.dto.request.UserWithdrawalRequest;
import com.example.senioron.domain.user.dto.response.*;
import com.example.senioron.domain.user.entity.User;
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

    @Operation(summary = "회원가입 이메일 인증 코드 발송", description = "회원가입 전 입력한 이메일로 인증 코드를 발송합니다.")
    @PostMapping("/signup/email/verification-code")
    public Response<SignupEmailVerificationCodeSendResponse> sendSignupEmailVerificationCode(
            @Valid @RequestBody SignupEmailVerificationCodeSendRequest request
    ) {
        return Response.ok(userService.sendSignupEmailVerificationCode(request));
    }

    @Operation(summary = "회원가입 이메일 인증 코드 확인", description = "회원가입 전 입력한 이메일 인증 코드를 확인합니다.")
    @PostMapping("/signup/email/verification-code/verify")
    public Response<SignupEmailVerificationCodeVerifyResponse> verifySignupEmailVerificationCode(
            @Valid @RequestBody SignupEmailVerificationCodeVerifyRequest request
    ) {
        return Response.ok(userService.verifySignupEmailVerificationCode(request));
    }

    @Operation(summary = "로그인", description = "아이디와 비밀번호로 로그인하고 JWT accessToken을 발급합니다.")
    @PostMapping("/login")
    public Response<UserLoginResponse> login(@Valid @RequestBody UserLoginRequest request) {
        return Response.ok(userService.login(request));
    }

    @Operation(summary = "Access Token 재발급", description = "Refresh Token으로 새 Access Token과 Refresh Token을 발급합니다.")
    @PostMapping("/token/refresh")
    public Response<TokenRefreshResponse> refreshToken(@Valid @RequestBody TokenRefreshRequest request) {
        return Response.ok(userService.refreshToken(request));
    }

    @Operation(summary = "회원 탈퇴", description = "현재 로그인한 사용자를 탈퇴 처리합니다. 확인 문구는 '회원 탈퇴'입니다.")
    @DeleteMapping("/me")
    public Response<Void> withdraw(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody UserWithdrawalRequest request
    ) {
        userService.withdraw(user, request);
        return Response.ok();
    }


    @Operation(summary = "로그아웃", description = "로그아웃하는 기기의 FCM 토큰을 비활성화합니다. 같은 기기에서 다른 계정으로 로그인해도 이전 계정에게 알림이 가지 않도록 합니다.")
    @PostMapping("/logout")
    public Response<Void> logout(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody UserLogoutRequest request
    ) {
        userService.logout(user, request.getDeviceIdentifier());
        return Response.ok();
    }

    @Operation(summary = "역할 선택/변경", description = "현재 로그인한 사용자의 역할을 선택하거나 변경합니다.")
    @PatchMapping("/me/role")
    public Response<UserRoleUpdateResponse> updateRole(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody UserRoleUpdateRequest request
    ) {
        return Response.ok(userService.updateRole(user, request));
    }


}
