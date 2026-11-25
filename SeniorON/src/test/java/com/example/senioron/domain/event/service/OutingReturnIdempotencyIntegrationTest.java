package com.example.senioron.domain.event.service;

import com.example.senioron.domain.event.dto.request.OutingReturnRequest;
import com.example.senioron.domain.event.entity.Event;
import com.example.senioron.domain.event.entity.OutingPhase;
import com.example.senioron.domain.event.repository.EventRepository;
import com.example.senioron.domain.event.util.GeocodingClient;
import com.example.senioron.domain.notification.dto.NotificationPreparationResult;
import com.example.senioron.domain.notification.service.NotificationService;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "cloud.aws.region=ap-northeast-2", "cloud.aws.s3.bucket=test-bucket",
        "spring.datasource.url=jdbc:h2:mem:outing-idempotency;MODE=MariaDB;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000"
})
@Timeout(30)
class OutingReturnIdempotencyIntegrationTest {
    @Autowired private EventService service;
    @Autowired private EventRepository events;
    @Autowired private UserRepository users;
    @MockitoBean private GeocodingClient geocoding;
    @MockitoBean private NotificationService notifications;
    private User parent;

    @BeforeEach
    void setUp() {
        events.deleteAll();
        users.deleteAll();
        parent = users.save(User.builder().loginId("outing-parent").name("부모님").role(Role.PARENT).build());
        when(geocoding.reverseGeocode(any(), any())).thenReturn("주소");
        when(notifications.createFormEvent(any(Event.class)))
                .thenReturn(NotificationPreparationResult.notDispatched("NO_RECEIVER"));
    }

    @Test
    void replayReturnsSnapshotWithoutGeocodingOrNotificationEvenIfPayloadOrPolicyChanges() {
        var first = service.createOutingReturnEvent(parent, request("key-1", OutingPhase.OUTING));
        clearInvocations(geocoding, notifications);
        when(notifications.createFormEvent(any())).thenReturn(NotificationPreparationResult.notDispatched("CHANGED"));
        var replay = service.createOutingReturnEvent(parent, request("key-1", OutingPhase.RETURN));
        assertThat(replay.getId()).isEqualTo(first.getId());
        assertThat(replay.getPhase()).isEqualTo(OutingPhase.OUTING);
        assertThat(replay.getNotificationStatus()).isEqualTo(first.getNotificationStatus());
        assertThat(replay.getReason()).isEqualTo("NO_RECEIVER");
        assertThat(replay.getReceiverCount()).isEqualTo(first.getReceiverCount());
        assertThat(events.count()).isEqualTo(1);
        verifyNoInteractions(geocoding, notifications);
    }

    @Test
    void concurrentDuplicatesCommitOneEventAndPrepareNotificationOnce() throws Exception {
        var barrier = new CyclicBarrier(2);
        when(geocoding.reverseGeocode(any(), any())).thenAnswer(call -> {
            barrier.await(10, TimeUnit.SECONDS);
            return "주소";
        });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> service.createOutingReturnEvent(parent, request("race", OutingPhase.OUTING)));
            var second = executor.submit(() -> service.createOutingReturnEvent(parent, request("race", OutingPhase.OUTING)));
            var a = first.get(20, TimeUnit.SECONDS);
            var b = second.get(20, TimeUnit.SECONDS);
            assertThat(a.getId()).isEqualTo(b.getId());
            assertThat(a.getReason()).isEqualTo(b.getReason()).isEqualTo("NO_RECEIVER");
            assertThat(events.count()).isEqualTo(1);
            verify(notifications, times(1)).createFormEvent(any(Event.class));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void legacyNullKeyAndDifferentUsersRemainIndependent() {
        service.createOutingReturnEvent(parent, request(null, OutingPhase.OUTING));
        service.createOutingReturnEvent(parent, request(null, OutingPhase.RETURN));
        service.createOutingReturnEvent(parent, request("shared", OutingPhase.OUTING));
        var another = users.save(User.builder().loginId("other-parent").name("다른 부모님").role(Role.PARENT).build());
        service.createOutingReturnEvent(another, request("shared", OutingPhase.RETURN));
        assertThat(events.count()).isEqualTo(4);
        verify(notifications, times(4)).createFormEvent(any(Event.class));
    }

    private OutingReturnRequest request(String key, OutingPhase phase) {
        return OutingReturnRequest.builder().phase(phase).latitude(BigDecimal.ONE).longitude(BigDecimal.ONE)
                .deviceBattery(80).clientEventId(key).build();
    }
}
