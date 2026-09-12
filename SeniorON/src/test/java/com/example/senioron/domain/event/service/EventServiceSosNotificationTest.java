package com.example.senioron.domain.event.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.senioron.domain.device.entity.Device;
import com.example.senioron.domain.device.entity.DeviceStatus;
import com.example.senioron.domain.device.repository.DeviceRepository;
import com.example.senioron.domain.event.dto.request.SosEventRequest;
import com.example.senioron.domain.event.dto.response.SosEventResponse;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.repository.FamilyRepository;
import com.example.senioron.domain.notification.repository.NotificationRepository;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

/**
 * 외부 트랜잭션이 아직 커밋되지 않은 동안 SOS 발송과 성공 지표가 발생하지 않는지 검증한다.
 * 각 테스트의 트랜잭션은 기본적으로 롤백된다.
 */
@SpringBootTest(properties = {
        "cloud.aws.region=ap-northeast-2",
        "cloud.aws.s3.bucket=test-bucket"
})
@Transactional
class EventServiceSosNotificationTest {

    @Autowired
    private EventService eventService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FamilyRepository familyRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private EntityManager entityManager;

    @Test
    void sosWithNoFamilyDefersMetricsUntilCommit() {
        User senior = userRepository.save(User.builder()
                .loginId("sos-no-family")
                .name("독거시니어")
                .role(Role.PARENT)
                .build());

        double eventSuccessBefore = counterValueOrZero("sos_event_total", "result", "success");
        double noReceiverBefore = counterValueOrZero("sos_dispatch_total", "result", "no_receiver");

        SosEventResponse response = eventService.createSosEvent(senior, sosRequest());

        assertThat(response.getReceiverCount()).isEqualTo(0);
        assertThat(counterValueOrZero("sos_event_total", "result", "success") - eventSuccessBefore)
                .isZero();
        assertThat(counterValueOrZero("sos_dispatch_total", "result", "no_receiver") - noReceiverBefore)
                .isZero();
    }

    @Test
    void sosWithChildDoesNotDispatchBeforeOuterCommit() {
        Family family = familyRepository.save(Family.builder()
                .familyCode("SOS-TEST-" + System.nanoTime())
                .build());

        User senior = userRepository.save(User.builder()
                .loginId("sos-parent-" + System.nanoTime())
                .name("시니어")
                .role(Role.PARENT)
                .family(family)
                .build());

        User child = userRepository.save(User.builder()
                .loginId("sos-child-" + System.nanoTime())
                .name("자녀")
                .role(Role.CHILD)
                .family(family)
                .build());

        deviceRepository.save(Device.builder()
                .user(child)
                .deviceIdentifier("device-1")
                .deviceToken("dummy-fcm-token")
                .connectionStatus(DeviceStatus.ONLINE)
                .build());

        entityManager.flush();

        // 실행 전후 델타로 비교해야 이 컨텍스트를 공유하는 다른 테스트의 누적값에 영향받지 않는다.
        double undeliveredBefore = counterValueOrZero("sos_dispatch_total", "result", "undelivered");
        double eventSuccessBefore = counterValueOrZero("sos_event_total", "result", "success");
        double notDeliveredBefore = sumNotDeliveredFcmSendCounters();

        SosEventResponse response = eventService.createSosEvent(senior, sosRequest());

        // 트랜잭션 내부에서 이벤트와 인앱 알림은 조회되지만 아직 커밋되지는 않았다.
        assertThat(response.getId()).isNotNull();
        assertThat(notificationRepository.findAll()).hasSize(1);

        // 기기 토큰이 있어도 외부 트랜잭션의 커밋 이전에는 발송하지 않는다.
        assertThat(response.getReceiverCount()).isEqualTo(1);
        double undeliveredAfter = counterValueOrZero("sos_dispatch_total", "result", "undelivered");
        assertThat(undeliveredAfter - undeliveredBefore).isZero();

        double eventSuccessAfter = counterValueOrZero("sos_event_total", "result", "success");
        assertThat(eventSuccessAfter - eventSuccessBefore).isZero();

        double notDeliveredAfter = sumNotDeliveredFcmSendCounters();
        assertThat(notDeliveredAfter - notDeliveredBefore).isZero();
    }

    private double sumNotDeliveredFcmSendCounters() {
        return counterValueOrZero("fcm_send_total", "result", "failed")
                + counterValueOrZero("fcm_send_total", "result", "skipped")
                + counterValueOrZero("fcm_send_total", "result", "token_invalid");
    }

    private SosEventRequest sosRequest() {
        SosEventRequest req = new SosEventRequest();
        ReflectionTestUtils.setField(req, "latitude", new BigDecimal("37.5665"));
        ReflectionTestUtils.setField(req, "longitude", new BigDecimal("126.9780"));
        ReflectionTestUtils.setField(req, "deviceBattery", 80);
        return req;
    }

    private double counterValueOrZero(String name, String tagKey, String tagValue) {
        var counter = meterRegistry.find(name).tag(tagKey, tagValue).counter();
        return counter != null ? counter.count() : 0.0;
    }
}
