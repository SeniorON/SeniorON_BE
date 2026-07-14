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
    private final UserService userService;

    public void send(String fcmToken, String title, String body) {
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
                userService.clearFcmToken(fcmToken);
            } else {
                log.warn("FCM 발송 일시 실패, 재시도 필요");
            }
        }
    }
}