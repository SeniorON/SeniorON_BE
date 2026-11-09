package com.example.senioron.domain.admin.service;

import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import java.nio.charset.StandardCharsets;

final class AdminPasswordPolicy {

    // BCrypt는 72바이트를 넘는 비밀번호를 인코딩하지 못한다.
    private static final int MAX_BYTES = 72;

    private AdminPasswordPolicy() {
    }

    static void validate(String password) {
        if (password == null || password.length() < 8 || password.length() > 64
                || password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES
                || !password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            throw new BusinessException(ErrorCode.INVALID_ADMIN_PASSWORD);
        }
    }
}
