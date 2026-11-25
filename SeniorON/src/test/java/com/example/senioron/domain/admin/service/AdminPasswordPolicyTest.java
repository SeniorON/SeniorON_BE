package com.example.senioron.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AdminPasswordPolicyTest {

    @Test
    void rejectsPasswordWithinCharacterLimitButOver72Bytes() {
        // 한글 1자는 UTF-8 3바이트: 26자, 74바이트
        String password = "a1" + "가".repeat(24);
        assertThat(password.length()).isLessThanOrEqualTo(64);
        assertThat(password.getBytes(StandardCharsets.UTF_8)).hasSize(74);

        assertThatThrownBy(() -> AdminPasswordPolicy.validate(password))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_ADMIN_PASSWORD));
    }

    @Test
    void acceptsPasswordUpTo72Bytes() {
        String exactly72 = "a1b" + "가".repeat(23);
        String under72 = "a1" + "가".repeat(23);
        assertThat(exactly72.getBytes(StandardCharsets.UTF_8)).hasSize(72);
        assertThat(under72.getBytes(StandardCharsets.UTF_8)).hasSize(71);

        assertThatCode(() -> AdminPasswordPolicy.validate(exactly72)).doesNotThrowAnyException();
        assertThatCode(() -> AdminPasswordPolicy.validate(under72)).doesNotThrowAnyException();
    }
}
