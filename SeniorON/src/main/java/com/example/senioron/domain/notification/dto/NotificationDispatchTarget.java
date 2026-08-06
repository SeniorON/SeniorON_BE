package com.example.senioron.domain.notification.dto;

import java.util.List;

/**
 * FCM 발송에 필요한 정보만 트랜잭션 안에서 미리 추출해 담아두는 객체.
 * 커밋 이후 발송 시점에는 영속성 컨텍스트가 닫혀 잇음
 * 엔티티를 그대로 넘기지 않고 지연 로딩이 필요 없는 형태로 복사해서 전달한다.
 */
public record NotificationDispatchTarget(
        Long receiverId,
        String title,
        String body,
        Long eventId,
        List<String> deviceTokens
) {
}
