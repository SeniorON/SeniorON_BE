package com.example.senioron.domain.companion.service;

import com.example.senioron.domain.companion.config.CompanionSafetyProperties;
import com.example.senioron.domain.event.util.FcmSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class CompanionSafetyPushServiceTest {

    private static final Long PARENT_USER_ID = 1L;

    private final CompanionSafetyRecipientService
            recipientService =
            mock(
                    CompanionSafetyRecipientService.class
            );

    private final FcmSender fcmSender =
            mock(FcmSender.class);

    private CompanionSafetyPushService service;

    @BeforeEach
    void setUp() {
        CompanionSafetyProperties properties =
                new CompanionSafetyProperties();

        service =
                new CompanionSafetyPushService(
                        recipientService,
                        properties,
                        fcmSender
                );
    }

    @Test
    void sendsNotificationToEveryChildToken() {
        given(
                recipientService.findChildTokens(
                        PARENT_USER_ID
                )
        ).willReturn(
                List.of(
                        "child-token-1",
                        "child-token-2"
                )
        );

        given(
                fcmSender.send(
                        "child-token-1",
                        "말벗 안전 확인 알림",
                        "부모님의 안전 확인이 필요합니다. 직접 연락해 상태를 확인해 주세요."
                )
        ).willReturn(true);

        given(
                fcmSender.send(
                        "child-token-2",
                        "말벗 안전 확인 알림",
                        "부모님의 안전 확인이 필요합니다. 직접 연락해 상태를 확인해 주세요."
                )
        ).willReturn(true);

        service.sendToChildren(
                PARENT_USER_ID
        );

        verify(fcmSender).send(
                "child-token-1",
                "말벗 안전 확인 알림",
                "부모님의 안전 확인이 필요합니다. 직접 연락해 상태를 확인해 주세요."
        );

        verify(fcmSender).send(
                "child-token-2",
                "말벗 안전 확인 알림",
                "부모님의 안전 확인이 필요합니다. 직접 연락해 상태를 확인해 주세요."
        );
    }

    @Test
    void continuesSendingWhenOneDeviceFails() {
        String title =
                "말벗 안전 확인 알림";

        String body =
                "부모님의 안전 확인이 필요합니다. 직접 연락해 상태를 확인해 주세요.";

        given(
                recipientService.findChildTokens(
                        PARENT_USER_ID
                )
        ).willReturn(
                List.of(
                        "failed-token",
                        "success-token"
                )
        );

        given(
                fcmSender.send(
                        "failed-token",
                        title,
                        body
                )
        ).willThrow(
                new RuntimeException(
                        "FCM test failure"
                )
        );

        given(
                fcmSender.send(
                        "success-token",
                        title,
                        body
                )
        ).willReturn(true);

        assertThatCode(() ->
                service.sendToChildren(
                        PARENT_USER_ID
                )
        ).doesNotThrowAnyException();

        verify(fcmSender).send(
                "failed-token",
                title,
                body
        );

        verify(fcmSender).send(
                "success-token",
                title,
                body
        );
    }

    @Test
    void doesNotCallFcmSenderWhenTokenDoesNotExist() {
        given(
                recipientService.findChildTokens(
                        PARENT_USER_ID
                )
        ).willReturn(List.of());

        service.sendToChildren(
                PARENT_USER_ID
        );

        verifyNoInteractions(fcmSender);
    }
}