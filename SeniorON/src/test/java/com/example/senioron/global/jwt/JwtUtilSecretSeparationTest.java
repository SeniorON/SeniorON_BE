package com.example.senioron.global.jwt;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JwtUtilSecretSeparationTest {
    private static final String USER_SECRET = "user-secret-key-12345678901234567890";
    private static final String ADMIN_SECRET = "admin-secret-key-1234567890123456789";

    @Test
    void sameUserAndAdminSecretsAreRejectedWithoutExposingSecret() {
        assertThatThrownBy(() -> jwt(USER_SECRET, USER_SECRET))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(USER_SECRET);
    }

    @Test
    void differentUserAndAdminSecretsAreAccepted() {
        assertThatCode(() -> jwt(USER_SECRET, ADMIN_SECRET))
                .doesNotThrowAnyException();
    }

    private JwtUtil jwt(String userSecret, String adminSecret) {
        return new JwtUtil(userSecret, 3_600_000L, 1_209_600_000L,
                adminSecret, 1_800_000L, 86_400_000L);
    }
}
