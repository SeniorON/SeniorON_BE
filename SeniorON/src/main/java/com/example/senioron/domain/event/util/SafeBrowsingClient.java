package com.example.senioron.domain.event.util;

import com.example.senioron.domain.event.entity.RiskCheckResult;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SafeBrowsingClient {
    private final RestClient safeBrowsingRestClient;

    @Value("${google.safebrowsing.api-key:}")
    private String apiKey;

    @PostConstruct
    public void validateConfig() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("google.safebrowsing.api-key가 설정되지 않았습니다. 위험 링크 감지 기능이 비활성화됩니다.");
        }
    }

    public RiskCheckResult checkUrl(String url) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("API 키 미설정으로 검사를 건너뜁니다: url={}", url);
            return RiskCheckResult.UNAVAILABLE;
        }

        try {
            SafeBrowsingRequest request = new SafeBrowsingRequest(
                    new SafeBrowsingRequest.Client("senioron", "1.0.0"),
                    new SafeBrowsingRequest.ThreatInfo(
                            List.of("MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE", "POTENTIALLY_HARMFUL_APPLICATION"),
                            List.of("ANY_PLATFORM"),
                            List.of("URL"),
                            List.of(new SafeBrowsingRequest.ThreatEntry(url))
                    )
            );

            SafeBrowsingResponse response = safeBrowsingRestClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v4/threatMatches:find")
                            .queryParam("key", apiKey)
                            .build())
                    .body(request)
                    .retrieve()
                    .body(SafeBrowsingResponse.class);

            boolean isDangerous = response != null && response.matches() != null && !response.matches().isEmpty();
            return isDangerous ? RiskCheckResult.DANGEROUS : RiskCheckResult.SAFE;

        } catch (Exception e) {
            log.warn("세이프 브라우징 검사 실패: {}", e.getMessage());
            return RiskCheckResult.UNAVAILABLE;   // false 대신 "판정 불가" 상태 반환
        }
    }
}