package com.example.senioron.domain.user.controller;

import com.example.senioron.domain.user.dto.request.UserSignUpRequest;
import com.example.senioron.domain.user.dto.response.LoginIdCheckResponse;
import com.example.senioron.domain.user.dto.response.UserSignUpResponse;
import com.example.senioron.domain.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "회원", description = "회원가입, 로그인, 계정 관련 API")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "아이디 중복 확인", description = "사용 가능한 아이디인지 중복 검사를 합니다.")
    @GetMapping("/check-login-id")
    public LoginIdCheckResponse checkLoginId(@RequestParam String loginId) {
        return userService.checkLoginId(loginId);
    }


    @Operation(summary = "회원가입", description = "이메일 인증 후 아이디, 비밀번호, 약관 동의 정보를 바탕으로 회원가입을 진행합니다.")
    @PostMapping("/signup")
    public UserSignUpResponse signUp(@Valid @RequestBody UserSignUpRequest request) {
        return userService.signUp(request);
    }
}