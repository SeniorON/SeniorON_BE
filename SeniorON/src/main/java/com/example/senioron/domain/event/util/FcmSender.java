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

@Component
@RequiredArgsConstructor
@Slf4j
public class FcmSender {

    // FCM 발송은 부수효과라 실패해도 API 응답에 반영하지 않음
    // 대신 모니터링 시스템에서 관리할 수 있도록 기록
    // 일시적 오류(503 UNAVAILABLE)에 대한 재시도는 Firebase Admin SDK가 내부적으로 최대 4회 수행
    // FirebaseConfig에서 요청 1회당 타임아웃을 걸어 무한정 대기하는 상황만 방지
    private static final String SEND_METRIC = "fcm_send_total";
    private static final String TAG_RESULT = "result";
    private static final String TAG_ERROR_CODE = "errorCode";

    private final FirebaseConfig firebaseConfig;
    private final DeviceRepository deviceRepository;
    private final ApplicationContext applicationContext;
    private final MeterRegistry meterRegistry;

    /**
     * 단일 시도 발송. SOS를 포함한 모든 알림이 이 메서드를 쓴다.
     * 일시적 오류 재시도는 SDK가 이미 하므로 우리 쪽에서 추가로 반복 호출하지 않는다.
     *
     * @return 발송 성공 여부
     */
    public boolean send(String fcmToken, String title, String body) {
        if (fcmToken == null || fcmToken.isBlank()) {
            countSend("skipped", "NO_TOKEN");
            log.warn("FCM 토큰이 비어있어 발송을 건너뜁니다.");
            return false;
        }

        if(!firebaseConfig.isInitialized()){
            countSend("skipped", "NOT_INITIALIZED");
            log.warn("Firebase가 초기화되지 않아 FCM 발송을 건너뜁니다.");
            return false;
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
            return true;
        } catch (FirebaseMessagingException e) {
            MessagingErrorCode errorCode = e.getMessagingErrorCode();
            String errorCodeName = (errorCode != null) ? errorCode.name() : "UNKNOWN";

            if (errorCode == MessagingErrorCode.UNREGISTERED){
                countSend("token_invalid", errorCodeName);
                log.warn("유효하지 않은 FCM 토큰, 삭제 처리");
                FcmSender self = applicationContext.getBean(FcmSender.class);
                self.clearInvalidToken(fcmToken);
                return false;
            }

            countSend("failed", errorCodeName);
            log.warn("FCM 발송 실패, token={}, errorCode={}", maskToken(fcmToken), errorCodeName);
            return false;
        } catch (RuntimeException e) {
            // FirebaseMessagingException 외의 예외는 기존대로 호출부가 처리하도록 전파시키고, 지표에만 남긴다.
            countSend("failed", "UNEXPECTED");
            throw e;
        }
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