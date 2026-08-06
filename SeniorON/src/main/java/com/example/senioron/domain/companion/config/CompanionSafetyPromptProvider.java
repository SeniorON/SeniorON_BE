package com.example.senioron.domain.companion.config;

import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
@Slf4j
public class CompanionSafetyPromptProvider {

    private final CompanionSafetyProperties properties;

    private String systemPrompt;

    @PostConstruct
    void load() {
        Resource resource = properties.getPromptResource();

        if (resource == null || !resource.exists()) {

            throw promptException();
        }

        try (InputStream input = resource.getInputStream()) {

            String loaded =
                    new String(
                            input.readAllBytes(),
                            StandardCharsets.UTF_8
                    ).trim();

            if (loaded.isBlank()) {
                throw promptException();
            }

            systemPrompt = loaded;

        } catch (IOException exception) {
            log.error(
                    "안전 분류 프롬프트를 불러오지 못했습니다.",
                    exception
            );

            throw promptException();
        }
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public String getVersion() {
        return properties.getPromptVersion();
    }

    private BusinessException promptException() {
        return new BusinessException(
                ErrorCode.COMPANION_SAFETY_CHECK_FAILED
        );
    }
}