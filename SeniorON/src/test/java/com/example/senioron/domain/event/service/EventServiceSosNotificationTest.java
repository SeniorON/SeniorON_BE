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
 * SOS 알림 발송 결과가 실제 Spring 컨텍스트(DB 트랜잭션 커밋, MeterRegistry, FcmSender 빈)를
 * 통해 응답과 지표에 반영되는지 확인한다.
 * <p>
 * 로컬 환경에는 Firebase 서비스 계정이 없어 FcmSender.send()는 항상 "초기화 안 됨"으로
 * 스킵된다. 따라서 여기서는 FCM 발송 성공 경로가 아니라, 두 가지 미전달 시나리오
 * (수신 대상 없음 / 발송 실패)가 응답과 지표에 정확히 반영되는지를 검증한다.
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
    void sosWithNoFamily_returnsZeroReceiverAndRecordsNoReceiverMetric() {
        User senior = userRepository.save(User.builder()
                .loginId("sos-no-family")
                .name("독거시니어")
                .role(Role.PARENT)
                .build());

        SosEventResponse response = eventService.createSosEvent(senior, sosRequest());

        assertThat(response.getReceiverCount()).isEqualTo(0);
        assertThat(response.getNotifiedCount()).isEqualTo(0);
        assertThat(counterValue("sos_dispatch_total", "result", "no_receiver")).isEqualTo(1.0);
    }

    @Test
    void sosWithChildButNoFirebase_returnsUndeliveredAndRecordsMetric() {
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

        SosEventResponse response = eventService.createSosEvent(senior, sosRequest());

        // 이벤트와 인앱 알림 레코드는 정상 저장된다 — 유실되는 건 푸시 채널뿐이다.
        assertThat(response.getId()).isNotNull();
        assertThat(notificationRepository.findAll()).hasSize(1);

        // 로컬에는 Firebase 자격증명이 없어 발송은 항상 실패(스킵)한다.
        assertThat(response.getReceiverCount()).isEqualTo(1);
        assertThat(response.getNotifiedCount()).isEqualTo(0);

        assertThat(counterValue("sos_dispatch_total", "result", "undelivered")).isEqualTo(1.0);
        assertThat(counterValue("fcm_send_total", "result", "skipped")).isGreaterThanOrEqualTo(1.0);
    }

    private SosEventRequest sosRequest() {
        SosEventRequest req = new SosEventRequest();
        ReflectionTestUtils.setField(req, "latitude", new BigDecimal("37.5665"));
        ReflectionTestUtils.setField(req, "longitude", new BigDecimal("126.9780"));
        ReflectionTestUtils.setField(req, "deviceBattery", 80);
        return req;
    }

    private double counterValue(String name, String tagKey, String tagValue) {
        var counter = meterRegistry.find(name).tag(tagKey, tagValue).counter();
        assertThat(counter).as("metric %s{%s=%s} not found", name, tagKey, tagValue).isNotNull();
        return counter.count();
    }
}
