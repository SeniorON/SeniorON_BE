package com.example.senioron.domain.companion.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment =
                SpringBootTest.WebEnvironment
                        .RANDOM_PORT,
        properties = {
                "spring.servlet.multipart.max-file-size=1KB",
                "spring.servlet.multipart.max-request-size=3KB",
                "cloud.aws.region=ap-northeast-2",
                "cloud.aws.s3.bucket=test-bucket"
        }
)
@Import(
        CompanionVoiceUploadLimitIntegrationTest
                .TestSecurityConfig.class
)
class CompanionVoiceUploadLimitIntegrationTest {

    @LocalServerPort
    private int port;

    private final HttpClient httpClient =
            HttpClient.newHttpClient();

    @Test
    void oversizedCompanionAudioReturnsCompanionError()
            throws Exception {

        UUID requestId =
                UUID.randomUUID();

        String boundary =
                "SeniorOnBoundary"
                        + UUID.randomUUID();

        byte[] requestBody =
                oversizedMultipartBody(
                        boundary
                );

        HttpRequest request =
                HttpRequest
                        .newBuilder()
                        .uri(
                                URI.create(
                                        "http://127.0.0.1:"
                                                + port
                                                + "/api/companion/"
                                                + "conversations/10/"
                                                + "voice-turn"
                                                + "?requestId="
                                                + requestId
                                )
                        )
                        .header(
                                "Content-Type",
                                "multipart/form-data; "
                                        + "boundary="
                                        + boundary
                        )
                        .POST(
                                HttpRequest
                                        .BodyPublishers
                                        .ofByteArray(
                                                requestBody
                                        )
                        )
                        .build();

        HttpResponse<String> response =
                httpClient.send(
                        request,
                        HttpResponse
                                .BodyHandlers
                                .ofString(
                                        StandardCharsets.UTF_8
                                )
                );

        assertThat(response.statusCode())
                .isEqualTo(413);

        assertThat(response.body())
                .contains(
                        "\"code\":\"COMPANION413_1\""
                );
    }

    private byte[] oversizedMultipartBody(
            String boundary
    ) {
        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        output.writeBytes(
                (
                        "--" + boundary + "\r\n"
                ).getBytes(
                        StandardCharsets.UTF_8
                )
        );

        output.writeBytes(
                (
                        "Content-Disposition: "
                                + "form-data; "
                                + "name=\"audio\"; "
                                + "filename=\"voice.mp3\"\r\n"
                ).getBytes(
                        StandardCharsets.UTF_8
                )
        );

        output.writeBytes(
                (
                        "Content-Type: "
                                + "audio/mpeg\r\n\r\n"
                ).getBytes(
                        StandardCharsets.UTF_8
                )
        );

        // 테스트 제한은 1KB이므로 2KB 파일로
        // 실제 multipart 용량 초과를 발생시킨다.
        output.writeBytes(
                new byte[2 * 1024]
        );

        output.writeBytes(
                (
                        "\r\n--"
                                + boundary
                                + "--\r\n"
                ).getBytes(
                        StandardCharsets.UTF_8
                )
        );

        return output.toByteArray();
    }

    @TestConfiguration(
            proxyBeanMethods = false
    )
    static class TestSecurityConfig {

        @Bean
        @Order(0)
        SecurityFilterChain
        companionVoiceTestSecurityFilterChain(
                HttpSecurity http
        ) throws Exception {

            http
                    .securityMatcher(
                            "/api/companion/"
                                    + "conversations/**"
                    )
                    .csrf(
                            csrf -> csrf.disable()
                    )
                    .authorizeHttpRequests(
                            authorization ->
                                    authorization
                                            .anyRequest()
                                            .permitAll()
                    );

            return http.build();
        }
    }
}