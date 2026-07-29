package com.example.senioron.domain.event.dto;

import com.example.senioron.domain.event.entity.Event;
import com.example.senioron.domain.notification.dto.NotificationDispatchTarget;

import java.util.List;

/**
 * SOS 이벤트 저장 결과와, 커밋 이후 발송할 알림 대상을 함께 전달하는 값 객체.
 * 발송을 분리하는 이유 : 외부 FCM 서버와 통신하는 동안 DB 커넥션을 물고 있으면 커넥션 풀 고갈 우려 등 문제 발생 예방
 */
public record SosEventCreation(
        Event event,
        List<NotificationDispatchTarget> dispatchTargets
) {
}
