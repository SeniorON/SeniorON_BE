package com.example.senioron.domain.companion.service;

import com.example.senioron.domain.companion.config.CompanionSafetyProperties;
import com.example.senioron.domain.event.util.FcmSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CompanionSafetyPushService {

    private final CompanionSafetyRecipientService recipientService;

    private final CompanionSafetyProperties properties;

    private final FcmSender fcmSender;

    public void sendToChildren(
            Long parentUserId
    ) {
        List<String> tokens =
                recipientService.findChildTokens(
                        parentUserId
                );

        for (String token : tokens) {
            try {
                fcmSender.send(
                        token,
                        properties
                                .getNotificationTitle(),
                        properties
                                .getNotificationBody()
                );
            } catch (RuntimeException exception) {
                log.warn(
                        "말벗 안전 알림 FCM 발송 중 예외가 발생했습니다.",
                        exception
                );
            }
        }
    }
}