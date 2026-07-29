package com.example.senioron.domain.notification.dto;

/**
 * 알림 발송 결과.
 * @param receiverCount 알림이 생성된 수신자 수. 0이면 가족이나 자녀가 등록되지 않아 알릴 대상 자체가 없었다는 뜻이다.
 * @param notifiedCount 그중에 기기 푸시 발송에 성공한 수신자 수. 하지만 FCM 접수 성공이 실제 단말 도달을 보장하지는 않는다.
 */
public record NotificationDispatchResult(
        int receiverCount,
        int notifiedCount
) {

    public static NotificationDispatchResult noReceiver() {
        return new NotificationDispatchResult(0, 0);
    }

    public boolean hasReceiver() {
        return receiverCount > 0;
    }

    public boolean isFullyDelivered() {
        return receiverCount > 0 && notifiedCount == receiverCount;
    }
}
