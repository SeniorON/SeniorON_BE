package com.example.senioron.domain.notification.dto;

import java.util.List;

/** 요청 시점의 발송 준비 결과. 실제 FCM 전달/기기 수신 결과가 아니다. */
public record NotificationPreparationResult(
        List<NotificationDispatchTarget> targets,
        Status notificationStatus,
        String reason
) {
    public enum Status { DISPATCH_REQUESTED, NOT_DISPATCHED }

    public NotificationPreparationResult {
        targets = List.copyOf(targets);
    }

    public static NotificationPreparationResult notDispatched(String reason) {
        return new NotificationPreparationResult(List.of(), Status.NOT_DISPATCHED, reason);
    }

    public static NotificationPreparationResult prepared(List<NotificationDispatchTarget> targets) {
        boolean hasToken = targets.stream().anyMatch(target -> !target.deviceTokens().isEmpty());
        return new NotificationPreparationResult(targets,
                hasToken ? Status.DISPATCH_REQUESTED : Status.NOT_DISPATCHED,
                hasToken ? null : "NO_DEVICE_TOKEN");
    }

    public int receiverCount() {
        return targets.size();
    }
}
