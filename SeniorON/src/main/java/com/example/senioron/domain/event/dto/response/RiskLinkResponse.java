package com.example.senioron.domain.event.dto.response;

import com.example.senioron.domain.event.entity.Event;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class RiskLinkResponse {

    private Long id;
    private String linkUrl;
    private String riskLevel;
    private LocalDateTime detectedAt;

    public static RiskLinkResponse of(Event event) {
        return RiskLinkResponse.builder()
                .id(event.getEventId())
                .linkUrl(event.getLinkUrl())
                .riskLevel(resolveRiskLevel(event.getIsDangerous()))
                .detectedAt(event.getCreatedAt())
                .build();
    }

    private static String resolveRiskLevel(Boolean isDangerous) {
        return Boolean.TRUE.equals(isDangerous) ? "높음" : "낮음";
    }
}
