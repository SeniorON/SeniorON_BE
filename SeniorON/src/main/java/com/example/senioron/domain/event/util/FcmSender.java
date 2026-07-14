package com.example.senioron.domain.event.util;

import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.domain.user.service.UserService;
import com.example.senioron.global.config.FirebaseConfig;
import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FcmSender {

    private final FirebaseConfig firebaseConfig;
    private final UserRepository userRepository;

    public void send(String fcmToken, String title, String body) {
        if (fcmToken == null || fcmToken.isBlank()) {
            log.warn("FCM 토큰이 비어있어 발송을 건너뜁니다.");
            return;
        }

        if(!firebaseConfig.isInitialized()){
            log.warn("Firebase가 초기화되지 않아 FCM 발송을 건너뜁니다.");
            return;
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
        } catch (FirebaseMessagingException e) {
            MessagingErrorCode errorCode = e.getMessagingErrorCode();

            if (errorCode == MessagingErrorCode.UNREGISTERED
                    || errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
                log.warn("유효하지 않은 FCM 토큰, 삭제 처리");
                userRepository.clearFcmToken(fcmToken);
            } else {
                log.warn("FCM 발송 일시 실패, 재시도 필요: token = " + maskToken(fcmToken));
            }
        }
    }
    private String maskToken(String token) {
        if (token == null || token.length() < 8) return "****";
        return token.substring(0, 8) + "...(masked)";
    }
}