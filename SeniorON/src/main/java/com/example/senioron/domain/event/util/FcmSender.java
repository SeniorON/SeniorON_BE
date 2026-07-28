package com.example.senioron.domain.event.util;

import com.example.senioron.domain.device.repository.DeviceRepository;
import com.example.senioron.global.config.FirebaseConfig;
import com.google.firebase.messaging.*;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class FcmSender {

    // FCM 발송은 부수효과라 실패해도 API 응답에 반영하지 않음
    // 대신 모니터링 시스템에서 관리할 수 있도록 기록
    // 단, SOS 긴급 알림만 예외적으로 sendWithRetry()를 쓰고 발송 결과를 응답에 반영한다.
    private static final String SEND_METRIC = "fcm_send_total";
    private static final String TAG_RESULT = "result";
    private static final String TAG_ERROR_CODE = "errorCode";

    private static final int MAX_SEND_ATTEMPTS = 3;
    private static final long RETRY_BASE_DELAY_MILLIS = 200L;

    // 재시도하면 성공할 수 있는 일시적 오류. 그 외(UNREGISTERED, INVALID_ARGUMENT 등)는 재시도해도 소용없다.
    private static final Set<MessagingErrorCode> RETRYABLE_ERROR_CODES = EnumSet.of(
            MessagingErrorCode.UNAVAILABLE,
            MessagingErrorCode.INTERNAL,
            MessagingErrorCode.QUOTA_EXCEEDED
    );

    private final FirebaseConfig firebaseConfig;
    private final DeviceRepository deviceRepository;
    private final ApplicationContext applicationContext;
    private final MeterRegistry meterRegistry;

    /**
     * 단일 시도 발송. 일반 알림용이며 실패해도 삼킨다.
     *
     * @return 발송 성공 여부
     */
    public boolean send(String fcmToken, String title, String body) {
        return attemptSend(fcmToken, title, body) == SendResult.SUCCESS;
    }

    /**
     * SOS 같은 긴급 알림용. 일시적 오류에 한해 짧은 백오프로 재시도한다.
     */
    public boolean sendWithRetry(String fcmToken, String title, String body) {
        for (int attempt = 1; attempt <= MAX_SEND_ATTEMPTS; attempt++) {
            SendResult result = attemptSend(fcmToken, title, body);

            if (result == SendResult.SUCCESS) {
                return true;
            }
            if (result != SendResult.RETRYABLE_FAILURE || attempt == MAX_SEND_ATTEMPTS) {
                return false;
            }
            if (!sleepBeforeRetry(attempt)) {
                return false;
            }
            log.warn("FCM 재시도, token={}, attempt={}/{}", maskToken(fcmToken), attempt + 1, MAX_SEND_ATTEMPTS);
        }
        return false;
    }

    private SendResult attemptSend(String fcmToken, String title, String body) {
        if (fcmToken == null || fcmToken.isBlank()) {
            countSend("skipped", "NO_TOKEN");
            log.warn("FCM 토큰이 비어있어 발송을 건너뜁니다.");
            return SendResult.SKIPPED;
        }

        if(!firebaseConfig.isInitialized()){
            countSend("skipped", "NOT_INITIALIZED");
            log.warn("Firebase가 초기화되지 않아 FCM 발송을 건너뜁니다.");
            return SendResult.SKIPPED;
        }
        Message message = Message.builder()
                .setToken(fcmToken)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .build();
        try {
            FirebaseMessaging.getInstance().send(message);
            countSend("success", "NONE");
            return SendResult.SUCCESS;
        } catch (FirebaseMessagingException e) {
            MessagingErrorCode errorCode = e.getMessagingErrorCode();
            String errorCodeName = (errorCode != null) ? errorCode.name() : "UNKNOWN";

            if (errorCode == MessagingErrorCode.UNREGISTERED){
                countSend("token_invalid", errorCodeName);
                log.warn("유효하지 않은 FCM 토큰, 삭제 처리");
                FcmSender self = applicationContext.getBean(FcmSender.class);
                self.clearInvalidToken(fcmToken);
                return SendResult.PERMANENT_FAILURE;
            }

            countSend("failed", errorCodeName);
            log.warn("FCM 발송 실패, token={}, errorCode={}", maskToken(fcmToken), errorCodeName);
            return RETRYABLE_ERROR_CODES.contains(errorCode)
                    ? SendResult.RETRYABLE_FAILURE
                    : SendResult.PERMANENT_FAILURE;
        } catch (RuntimeException e) {
            // FirebaseMessagingException 외의 예외는 기존대로 호출부가 처리하도록 전파시키고, 지표에만 남긴다.
            countSend("failed", "UNEXPECTED");
            throw e;
        }
    }

    /**
     * @return 재시도를 계속해도 되면 true, 인터럽트되어 중단해야 하면 false
     */
    private boolean sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(RETRY_BASE_DELAY_MILLIS * (1L << (attempt - 1)));
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private enum SendResult {
        SUCCESS,
        RETRYABLE_FAILURE,
        PERMANENT_FAILURE,
        SKIPPED
    }

    private void countSend(String result, String errorCode) {
        meterRegistry.counter(SEND_METRIC, TAG_RESULT, result, TAG_ERROR_CODE, errorCode).increment();
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 8) return "****";
        return token.substring(0, 8) + "...(masked)";
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void  clearInvalidToken(String token) {
        deviceRepository.clearDeviceToken(token);
    }
}