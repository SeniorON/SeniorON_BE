package com.example.senioron.domain.event.dto.response;

import com.example.senioron.domain.event.entity.Event;
import com.example.senioron.domain.notification.dto.NotificationPreparationResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class RiskLinkResponse {
    private Long seniorId;
    @Schema(description = "발송 준비 상태이며 FCM 전달 또는 기기 수신 성공을 의미하지 않습니다.")
    private NotificationPreparationResult.Status notificationStatus;
    private String reason;
    private int receiverCount;


    private Long id;
    private String linkUrl;
    private String riskLevel;
    private LocalDateTime detectedAt;

    public static RiskLinkResponse of(Event event, NotificationPreparationResult result) {
        RiskLinkResponse response = of(event);
        response.notificationStatus = result.notificationStatus();
        response.reason = result.reason();
        response.receiverCount = result.receiverCount();
        return response;
    }

    public static RiskLinkResponse of(Event event) {
        return RiskLinkResponse.builder()
                .seniorId(event.getSenior() == null ? null : event.getSenior().getSeniorId())
                .id(event.getEventId())
                .linkUrl(event.getLinkUrl())
                .riskLevel(resolveRiskLevel(event.getIsDangerous()))
                .detectedAt(event.getCreatedAt())
                .build();
    }

    private static String resolveRiskLevel(Boolean isDangerous) {
        if (isDangerous == null) {
            return "확인불가";
        }
        return isDangerous ? "높음" : "낮음";
    }
}
