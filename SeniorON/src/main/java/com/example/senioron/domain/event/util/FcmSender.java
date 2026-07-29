package com.example.senioron.domain.event.util;

import com.example.senioron.domain.device.repository.DeviceRepository;
import com.example.senioron.global.config.FirebaseConfig;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
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

    private static final String SEND_METRIC = "fcm_send_total";
    private static final String TAG_RESULT = "result";
    private static final String TAG_ERROR_CODE = "errorCode";

    private final FirebaseConfig firebaseConfig;
    private final DeviceRepository deviceRepository;
    private final ApplicationContext applicationContext;
    private final MeterRegistry meterRegistry;

    public boolean send(String fcmToken, String title, String body) {
        if (fcmToken == null || fcmToken.isBlank()) {
            countSend("skipped", "NO_TOKEN");
            log.warn("FCM 토큰이 비어있어 발송을 건너뜁니다.");
            return false;
        }

        if (!firebaseConfig.isInitialized()) {
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
            String errorCodeName = errorCode != null
                    ? errorCode.name()
                    : "UNKNOWN";

            if (errorCode == MessagingErrorCode.UNREGISTERED) {
                countSend("token_invalid", errorCodeName);
                log.warn("유효하지 않은 FCM 토큰, 삭제 처리");

                FcmSender self = applicationContext.getBean(FcmSender.class);
                self.clearInvalidToken(fcmToken);
                return false;
            }

            countSend("failed", errorCodeName);
            log.warn(
                    "FCM 발송 실패, token={}, errorCode={}",
                    maskToken(fcmToken),
                    errorCodeName
            );
            return false;
        } catch (RuntimeException e) {
            countSend("failed", "UNEXPECTED");
            throw e;
        }
    }

    public void sendData(
            String fcmToken,
            Map<String, String> data
    ) {
        if (fcmToken == null || fcmToken.isBlank()) {
            countSend("skipped", "NO_TOKEN");
            log.warn("FCM 토큰이 비어있어 발송을 건너뜁니다.");
            return;
        }

        if (!firebaseConfig.isInitialized()) {
            countSend("skipped", "NOT_INITIALIZED");
            log.warn("Firebase가 초기화되지 않아 FCM 발송을 건너뜁니다.");
            return;
        }

        if (data == null || data.isEmpty()) {
            countSend("skipped", "NO_DATA");
            log.warn("FCM data가 비어있어 발송을 건너뜁니다.");
            return;
        }

        Message message = Message.builder()
                .setToken(fcmToken)
                .putAllData(data)
                .setAndroidConfig(
                        AndroidConfig.builder()
                                .setPriority(AndroidConfig.Priority.HIGH)
                                .build()
                )
                .build();

        try {
            String messageId = FirebaseMessaging.getInstance().send(message);

            countSend("success", "NONE");
            log.info(
                    "복약 FCM data 발송 성공, messageId={}, token={}",
                    messageId,
                    maskToken(fcmToken)
            );
        } catch (FirebaseMessagingException e) {
            MessagingErrorCode errorCode = e.getMessagingErrorCode();
            String errorCodeName = errorCode != null
                    ? errorCode.name()
                    : "UNKNOWN";

            if (errorCode == MessagingErrorCode.UNREGISTERED) {
                countSend("token_invalid", errorCodeName);
                log.warn("유효하지 않은 FCM 토큰, 삭제 처리");

                FcmSender self = applicationContext.getBean(FcmSender.class);
                self.clearInvalidToken(fcmToken);
                return;
            }

            countSend("failed", errorCodeName);
            log.warn(
                    "FCM 발송 실패, token={}, errorCode={}",
                    maskToken(fcmToken),
                    errorCodeName
            );
        } catch (RuntimeException e) {
            countSend("failed", "UNEXPECTED");
            throw e;
        }
    }

    private void countSend(String result, String errorCode) {
        meterRegistry.counter(
                SEND_METRIC,
                TAG_RESULT,
                result,
                TAG_ERROR_CODE,
                errorCode
        ).increment();
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 8) {
            return "****";
        }

        return token.substring(0, 8) + "...(masked)";
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void clearInvalidToken(String token) {
        deviceRepository.clearDeviceToken(token);
    }
}