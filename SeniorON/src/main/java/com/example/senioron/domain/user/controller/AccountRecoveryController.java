package com.example.senioron.domain.user.controller;

import com.example.senioron.domain.user.dto.request.LoginIdFindRequest;
import com.example.senioron.domain.user.dto.request.PasswordResetRequest;
import com.example.senioron.domain.user.dto.request.PasswordResetCodeSendRequest;
import com.example.senioron.domain.user.dto.request.PasswordResetCodeVerifyRequest;
import com.example.senioron.domain.user.dto.response.LoginIdFindResponse;
import com.example.senioron.domain.user.dto.response.PasswordResetResponse;
import com.example.senioron.domain.user.dto.response.PasswordResetCodeSendResponse;
import com.example.senioron.domain.user.dto.response.PasswordResetCodeVerifyResponse;
import com.example.senioron.domain.user.service.ClientIpResolver;
import com.example.senioron.domain.user.service.AccountRecoveryService;
import com.example.senioron.global.apiPayload.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
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
    private final ClientIpResolver clientIpResolver;

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
            @Valid @RequestBody PasswordResetCodeSendRequest request,
            HttpServletRequest httpServletRequest
    ) {
        return Response.ok(accountRecoveryService.sendPasswordResetVerificationCode(
                request,
                clientIpResolver.resolve(httpServletRequest)
        ));
    }

    @Operation(
            summary = "비밀번호 재설정 인증번호 확인",
            description = "비밀번호 재설정 인증번호 발송 API에서 응답받은 인증번호 ID와 사용자가 입력한 인증번호가 일치하는지 확인합니다."
    )
    @PostMapping("/password/verification-code/verify")
    public Response<PasswordResetCodeVerifyResponse> verifyPasswordResetVerificationCode(
            @Valid @RequestBody PasswordResetCodeVerifyRequest request
    ) {
        return Response.ok(accountRecoveryService.verifyPasswordResetVerificationCode(request));
    }

    @Operation(
            summary = "비밀번호 재설정",
            description = "비밀번호를 잊은 사용자가 이메일 인증을 완료한 뒤 새 비밀번호를 설정합니다. 인증번호 확인 작업을 먼저 하지 않으면 에러메세지가 출력되니 반드시 인증번호 확인 API를 실행해 주세요!!"
    )
    @PatchMapping("/password")
    public Response<PasswordResetResponse> resetPassword(
            @Valid @RequestBody PasswordResetRequest request
    ) {
        return Response.ok(accountRecoveryService.resetPassword(request));
    }
}
