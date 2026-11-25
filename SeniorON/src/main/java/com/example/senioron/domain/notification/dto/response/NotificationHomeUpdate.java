package com.example.senioron.domain.notification.dto.response;

import com.example.senioron.domain.notification.entity.NotificationType;

/** 알림 메인 재조회 신호. 화면 데이터는 기존 조회 API에서 가져온다. */
public record NotificationHomeUpdate(
        String action,
        Long seniorId,
        Long notificationId,
        Long eventId,
        NotificationType type,
        String reason
) {
}
