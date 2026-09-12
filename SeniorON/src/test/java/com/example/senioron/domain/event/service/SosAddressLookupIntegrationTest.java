package com.example.senioron.domain.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.senioron.domain.device.entity.Device;
import com.example.senioron.domain.device.entity.DeviceStatus;
import com.example.senioron.domain.device.repository.DeviceRepository;
import com.example.senioron.domain.event.dto.request.SosEventRequest;
import com.example.senioron.domain.event.repository.EventRepository;
import com.example.senioron.domain.event.util.FcmSender;
import com.example.senioron.domain.event.util.GeocodingClient;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.repository.FamilyRepository;
import com.example.senioron.domain.notification.repository.NotificationRepository;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
        "cloud.aws.region=ap-northeast-2",
        "cloud.aws.s3.bucket=test-bucket",
        "spring.datasource.url=jdbc:h2:mem:sos-address-test;MODE=MariaDB;DB_CLOSE_DELAY=-1"
})
@Timeout(20)
class SosAddressLookupIntegrationTest {

    private static final BigDecimal LATITUDE = new BigDecimal("37.5665");
    private static final BigDecimal LONGITUDE = new BigDecimal("126.9780");

    @Autowired private EventService eventService;
    @Autowired private EventRepository eventRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private FamilyRepository familyRepository;
    @Autowired private DeviceRepository deviceRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired @Qualifier("sosAddressExecutor") private ThreadPoolTaskExecutor addressExecutor;

    @MockitoBean private GeocodingClient geocodingClient;
    @MockitoBean private FcmSender fcmSender;

    private final ExecutorService requestExecutor = Executors.newSingleThreadExecutor();
    private final CountDownLatch releaseLookup = new CountDownLatch(1);
    private User senior;

    @BeforeEach
    void setUp() {
        senior = transactionTemplate.execute(status -> {
            Family family = familyRepository.save(Family.builder().familyCode("SOS-ADDRESS").build());
            User parent = userRepository.save(User.builder()
                    .loginId("address-parent").name("부모님").role(Role.PARENT).family(family).build());
            User child = userRepository.save(User.builder()
                    .loginId("address-child").name("자녀").role(Role.CHILD).family(family).build());
            deviceRepository.save(Device.builder().user(child).deviceIdentifier("address-device")
                    .deviceToken("child-token").connectionStatus(DeviceStatus.ONLINE).build());
            return parent;
        });
        given(fcmSender.sendHighPriority(anyString(), anyString(), anyString(), anyLong())).willReturn(true);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        releaseLookup.countDown();
        requestExecutor.shutdown();
        assertThat(requestExecutor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        await().atMost(Duration.ofSeconds(5)).until(() ->
                addressExecutor.getActiveCount() == 0 && addressExecutor.getQueueSize() == 0);
        transactionTemplate.executeWithoutResult(status -> {
            notificationRepository.deleteAllInBatch();
            eventRepository.deleteAllInBatch();
            deviceRepository.deleteAllInBatch();
            userRepository.deleteAllInBatch();
            familyRepository.deleteAllInBatch();
        });
    }

    @Test
    void slowLookupDoesNotDelaySosPushOrResponseAndUpdatesCommittedEventLater() throws Exception {
        CountDownLatch lookupStarted = new CountDownLatch(1);
        AtomicBoolean lookupHeldTransaction = new AtomicBoolean(true);
        AtomicBoolean eventWasCommitted = new AtomicBoolean(false);
        given(geocodingClient.reverseGeocode(LATITUDE, LONGITUDE)).willAnswer(invocation -> {
            lookupHeldTransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
            eventWasCommitted.set(eventRepository.count() == 1);
            lookupStarted.countDown();
            if (!releaseLookup.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Test did not release address lookup");
            }
            return "서울특별시 중구";
        });

        var pendingResponse = requestExecutor.submit(() -> eventService.createSosEvent(senior, sosRequest()));
        assertThat(lookupStarted.await(3, TimeUnit.SECONDS)).isTrue();
        var response = pendingResponse.get(2, TimeUnit.SECONDS);

        assertThat(response.getAddress()).isEqualTo(SosAddressLookupService.PENDING_ADDRESS);
        assertThat(response.getLatitude()).isEqualByComparingTo(LATITUDE);
        assertThat(response.getLongitude()).isEqualByComparingTo(LONGITUDE);
        assertThat(response.getReceiverCount()).isEqualTo(1);
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                verify(fcmSender).sendHighPriority("child-token", "SOS 알림", "도움이 필요해요", response.getId()));
        assertThat(lookupHeldTransaction).isFalse();
        assertThat(eventWasCommitted).isTrue();

        releaseLookup.countDown();
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            var detail = eventService.getEventDetail(senior, response.getId());
            assertThat(detail.getAddress()).isEqualTo("서울특별시 중구");
            assertThat(detail.getLatitude()).isEqualByComparingTo(LATITUDE);
            assertThat(detail.getLongitude()).isEqualByComparingTo(LONGITUDE);
        });
        // 주소 갱신으로 SOS 이벤트나 푸시를 중복 생성하지 않는다.
        assertThat(eventRepository.count()).isEqualTo(1);
        assertThat(notificationRepository.count()).isEqualTo(1);
        verify(fcmSender).sendHighPriority("child-token", "SOS 알림", "도움이 필요해요", response.getId());
    }

    @Test
    void rolledBackOuterTransactionDoesNotStartAddressLookupOrPush() {
        transactionTemplate.executeWithoutResult(status -> {
            eventService.createSosEvent(senior, sosRequest());
            verifyNoInteractions(geocodingClient, fcmSender);
            status.setRollbackOnly();
        });

        await().during(Duration.ofMillis(300)).atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> verifyNoInteractions(geocodingClient, fcmSender));
        assertThat(eventRepository.count()).isZero();
        assertThat(notificationRepository.count()).isZero();
    }

    @Test
    void outerTransactionCommitsBeforePushAndSlowPushDoesNotBlockResponse() throws Exception {
        CountDownLatch sendStarted = new CountDownLatch(1);
        CountDownLatch releaseSend = new CountDownLatch(1);
        AtomicBoolean committed = new AtomicBoolean();
        given(fcmSender.sendHighPriority(anyString(), anyString(), anyString(), anyLong())).willAnswer(call -> {
            committed.set(eventRepository.count() == 1 && notificationRepository.count() == 1);
            sendStarted.countDown();
            if (!releaseSend.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("FCM test latch timed out");
            return true;
        });
        try {
            var response = requestExecutor.submit(() -> transactionTemplate.execute(status -> {
                var created = eventService.createSosEvent(senior, sosRequest());
                verifyNoInteractions(geocodingClient, fcmSender);
                return created;
            }));
            assertThat(response.get(2, TimeUnit.SECONDS).getReceiverCount()).isEqualTo(1);
            assertThat(sendStarted.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(committed).isTrue();
        } finally {
            releaseSend.countDown();
        }
    }

    @Test
    void lookupFailureDoesNotFailSosAndKeepsCoordinates() {
        given(geocodingClient.reverseGeocode(LATITUDE, LONGITUDE))
                .willThrow(new IllegalStateException("Kakao unavailable"));

        var response = eventService.createSosEvent(senior, sosRequest());

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            var saved = eventRepository.findById(response.getId()).orElseThrow();
            assertThat(saved.getAddress()).isEqualTo("위치정보를 확인할 수 없어요");
            assertThat(saved.getLatitude()).isEqualByComparingTo(LATITUDE);
            assertThat(saved.getLongitude()).isEqualByComparingTo(LONGITUDE);
        });
    }

    private SosEventRequest sosRequest() {
        SosEventRequest request = new SosEventRequest();
        ReflectionTestUtils.setField(request, "latitude", LATITUDE);
        ReflectionTestUtils.setField(request, "longitude", LONGITUDE);
        ReflectionTestUtils.setField(request, "deviceBattery", 80);
        return request;
    }
}
