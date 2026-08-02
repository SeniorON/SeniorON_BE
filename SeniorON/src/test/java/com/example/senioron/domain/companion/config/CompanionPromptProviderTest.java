package com.example.senioron.domain.companion.config;

import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CompanionPromptProviderTest {

    @Test
    void 프롬프트를_읽고_현재_날짜와_설정값을_반환한다() {
        CompanionPromptProperties properties =
                new CompanionPromptProperties();

        properties.setResource(
                new ByteArrayResource(
                        " 테스트 시스템 프롬프트 "
                                .getBytes(
                                        StandardCharsets.UTF_8
                                )
                )
        );

        properties.setVersion("v1");
        properties.setRecentMessageLimit(20);

        CompanionPromptProvider provider =
                new CompanionPromptProvider(
                        properties
                );

        provider.load();

        String systemPrompt =
                provider.getSystemPrompt();

        assertThat(systemPrompt)
                .contains(
                        "테스트 시스템 프롬프트"
                );

        assertThat(systemPrompt)
                .contains(
                        LocalDate.now(
                                ZoneId.of(
                                        "Asia/Seoul"
                                )
                        ).toString()
                );

        assertThat(provider.getVersion())
                .isEqualTo("v1");

        assertThat(
                provider.getRecentMessageLimit()
        ).isEqualTo(20);
    }

    @Test
    void 프롬프트_파일이_없으면_오류가_발생한다() {
        CompanionPromptProperties properties =
                new CompanionPromptProperties();

        properties.setResource(
                new ClassPathResource(
                        "prompts/missing-prompt.txt"
                )
        );

        CompanionPromptProvider provider =
                new CompanionPromptProvider(
                        properties
                );

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        provider::load
                );

        assertThat(exception.getCode())
                .isEqualTo(
                        ErrorCode
                                .COMPANION_PROMPT_NOT_FOUND
                );
    }

    @Test
    void 프롬프트가_공백이면_오류가_발생한다() {
        CompanionPromptProperties properties =
                new CompanionPromptProperties();

        properties.setResource(
                new ByteArrayResource(
                        "   "
                                .getBytes(
                                        StandardCharsets.UTF_8
                                )
                )
        );

        CompanionPromptProvider provider =
                new CompanionPromptProvider(
                        properties
                );

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        provider::load
                );

        assertThat(exception.getCode())
                .isEqualTo(
                        ErrorCode
                                .COMPANION_PROMPT_NOT_FOUND
                );
    }

    @Test
    void 기본_프롬프트_리소스를_로딩한다() {
        CompanionPromptProperties properties =
                new CompanionPromptProperties();

        CompanionPromptProvider provider =
                new CompanionPromptProvider(
                        properties
                );

        provider.load();

        assertThat(
                provider.getSystemPrompt()
        ).isNotBlank();

        assertThat(
                provider.getSystemPrompt()
        ).contains(
                "현재 날짜(한국 기준)"
        );
    }
}