package com.example.senioron.domain.user.controller;

import com.example.senioron.domain.user.dto.request.LoginIdFindRequest;
import com.example.senioron.domain.user.dto.request.PasswordResetCodeSendRequest;
import com.example.senioron.domain.user.dto.response.LoginIdFindResponse;
import com.example.senioron.domain.user.dto.response.PasswordResetCodeSendResponse;
import com.example.senioron.domain.user.service.AccountRecoveryService;
import com.example.senioron.global.apiPayload.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "계정 찾기", description = "아이디 및 비밀번호 찾기 관련 API")
@RestController
@RequestMapping("/api/users/account-recovery")
@RequiredArgsConstructor
public class AccountRecoveryController {

    private final AccountRecoveryService accountRecoveryService;

    @Operation(
            summary = "로그인 아이디 찾기",
            description = "이름과 이메일이 모두 일치하는 사용자의 로그인 아이디를 조회합니다."
    )
    @PostMapping("/login-id")
    public Response<LoginIdFindResponse> findLoginId(
            @Valid @RequestBody LoginIdFindRequest request
    ) {
        return Response.ok(accountRecoveryService.findLoginId(request));
    }

    @Operation(
            summary = "비밀번호 재설정 인증번호 발송",
            description = "이름과 로그인 아이디가 모두 일치하는 사용자의 이메일로 비밀번호 재설정 인증번호를 발송합니다."
    )
    @PostMapping("/password/verification-code")
    public Response<PasswordResetCodeSendResponse> sendPasswordResetVerificationCode(
            @Valid @RequestBody PasswordResetCodeSendRequest request
    ) {
        return Response.ok(accountRecoveryService.sendPasswordResetVerificationCode(request));
    }
}
