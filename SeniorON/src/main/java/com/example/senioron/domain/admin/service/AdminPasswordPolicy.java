package com.example.senioron.domain.admin.service;

import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;

final class AdminPasswordPolicy {

    private AdminPasswordPolicy() {
    }

    static void validate(String password) {
        if (password == null || password.length() < 8 || password.length() > 64
                || !password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            throw new BusinessException(ErrorCode.INVALID_ADMIN_PASSWORD);
        }
    }
}
