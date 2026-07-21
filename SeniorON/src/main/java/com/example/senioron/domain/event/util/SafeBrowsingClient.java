package com.example.senioron.domain.event.util;

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

    public boolean isDangerous(String url) {
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

            return response != null && response.matches() != null && !response.matches().isEmpty();
        } catch (Exception e) {
            log.warn("세이프 브라우징 검사 실패: {}", e.getMessage());
            return false;
        }
    }
}
