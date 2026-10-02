package com.example.senioron.domain.family.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.senioron.domain.event.util.FcmSender;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FamilyPhotoPushServiceTest {

    private final FamilyPhotoPushTargetService targets = mock(FamilyPhotoPushTargetService.class);
    private final FcmSender fcmSender = mock(FcmSender.class);
    private final FamilyPhotoPushService service = new FamilyPhotoPushService(targets, fcmSender);

    @Test
    void sendsPhotoTypeAndIdAndContinuesAfterRejectedAndExceptionalSends() {
        given(targets.load(10L)).willReturn(Optional.of(
                new FamilyPhotoPushTargetService.PushTarget("자녀님이 공유했어요.",
                        List.of("rejected", "exception", "accepted"))));
        given(fcmSender.sendData(eq("rejected"), anyMap())).willReturn(false);
        given(fcmSender.sendData(eq("exception"), anyMap()))
                .willThrow(new IllegalStateException("FCM unavailable"));
        given(fcmSender.sendData(eq("accepted"), anyMap())).willReturn(true);

        assertThatCode(() -> service.send(10L)).doesNotThrowAnyException();

        Map<String, String> payload = Map.of(
                "type", "FAMILY_PHOTO_SHARED", "familyPhotoId", "10",
                "title", "새 가족사진", "body", "자녀님이 공유했어요.");
        for (String token : List.of("rejected", "exception", "accepted")) {
            verify(fcmSender).sendData(token, payload);
        }
    }

    @Test
    void skipsFcmWhenThereAreNoRecipients() {
        given(targets.load(10L)).willReturn(Optional.empty());
        service.send(10L);
        verifyNoInteractions(fcmSender);
    }

    @Test
    void containsRecipientLookupFailure() {
        given(targets.load(10L)).willThrow(new IllegalStateException("DB unavailable"));
        assertThatCode(() -> service.send(10L)).doesNotThrowAnyException();
        verifyNoInteractions(fcmSender);
    }
}
