package com.example.senioron.domain.companion.config;

import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
public class CompanionPromptProvider {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    private final CompanionPromptProperties properties;

    private String basePrompt;

    @PostConstruct
    void load() {
        Resource resource = properties.getResource();

        if (resource == null || !resource.exists()) {
            throw promptException();
        }

        try (InputStream input = resource.getInputStream()) {

            String loaded = new String(
                    input.readAllBytes(),
                    StandardCharsets.UTF_8
            ).trim();

            if (loaded.isBlank()) {
                throw promptException();
            }

            basePrompt = loaded;
        } catch (IOException exception) {
            throw promptException();
        }
    }

    public String getSystemPrompt() {
        LocalDate currentDate = LocalDate.now(SEOUL_ZONE);

        return basePrompt
                + System.lineSeparator()
                + System.lineSeparator()
                + "현재 날짜(한국 기준): "
                + currentDate;
    }

    public String getVersion() {
        return properties.getVersion();
    }

    public int getRecentMessageLimit() {
        return properties
                .getRecentMessageLimit();
    }

    private BusinessException
    promptException() {
        return new BusinessException(ErrorCode.COMPANION_PROMPT_NOT_FOUND);
    }
}