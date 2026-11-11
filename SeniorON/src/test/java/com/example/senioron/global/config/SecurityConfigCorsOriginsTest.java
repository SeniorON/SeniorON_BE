package com.example.senioron.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SecurityConfigCorsOriginsTest {

    @Test
    void parsesCommaSeparatedOriginsTrimmingWhitespace() {
        assertThat(SecurityConfig.parseAllowedOrigins(
                " https://senioron.site , http://localhost:5173,http://localhost:5174,http://localhost:3000 "
        )).containsExactly(
                "https://senioron.site",
                "http://localhost:5173",
                "http://localhost:5174",
                "http://localhost:3000"
        );
    }

    @Test
    void skipsBlankEntries() {
        assertThat(SecurityConfig.parseAllowedOrigins("http://localhost:5173,, ,http://localhost:3000,"))
                .containsExactly("http://localhost:5173", "http://localhost:3000");
    }

    @Test
    void returnsEmptyListForBlankOrNullValue() {
        assertThat(SecurityConfig.parseAllowedOrigins("  ")).isEmpty();
        assertThat(SecurityConfig.parseAllowedOrigins(null)).isEmpty();
    }
}
