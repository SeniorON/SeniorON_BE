package com.example.senioron.domain.event.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.senioron.domain.device.entity.Device;
import com.example.senioron.domain.device.entity.DeviceStatus;
import com.example.senioron.domain.device.repository.DeviceRepository;
import com.example.senioron.domain.event.dto.request.SosEventRequest;
import com.example.senioron.domain.event.dto.response.SosEventResponse;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyMember;
import com.example.senioron.domain.family.repository.FamilyMemberRepository;
import com.example.senioron.domain.family.repository.FamilyRepository;
import com.example.senioron.domain.notification.repository.NotificationRepository;
import com.example.senioron.domain.notification.dto.response.NotificationListResponse;
import com.example.senioron.domain.notification.entity.NotificationType;
import com.example.senioron.domain.notification.service.NotificationService;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.repository.SeniorRepository;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
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
    private NotificationService notificationService;

    @Autowired
    private SeniorRepository seniorRepository;

    @Autowired
    private FamilyMemberRepository familyMemberRepository;

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
        assertThat(response.getSeniorId()).isNull();
        assertThat(response.getNotificationStatus().name()).isEqualTo("NOT_DISPATCHED");
        assertThat(response.getReason()).isEqualTo("SENIOR_NOT_LINKED");
        assertThat(counterValueOrZero("sos_event_total", "result", "success") - eventSuccessBefore)
                .isZero();
        assertThat(counterValueOrZero("sos_dispatch_total", "result", "no_receiver") - noReceiverBefore)
                .isZero();
    }

    @Test
    void sosWithChildDoesNotDispatchBeforeOuterCommit() {
        Family family = familyRepository.save(Family.builder()
                .seniorCode("SOS-TEST-" + System.nanoTime())
                .build());
        Family unrelatedFamily = familyRepository.save(Family.builder()
                .seniorCode("SOS-UNRELATED-" + System.nanoTime())
                .build());

        User senior = userRepository.save(User.builder()
                .loginId("sos-parent-" + System.nanoTime())
                .name("시니어")
                .role(Role.PARENT)
                .build());
        addFamilyMember(senior, family, ManagerType.NONE);

        User child = userRepository.save(User.builder()
                .loginId("sos-child-" + System.nanoTime())
                .name("자녀")
                .role(Role.CHILD)
                .build());
        addFamilyMember(child, family, ManagerType.PRIMARY);

        User unrelatedChild = userRepository.save(User.builder()
                .loginId("sos-unrelated-child-" + System.nanoTime())
                .name("다른 시니어 담당 자녀")
                .role(Role.CHILD)
                .build());
        addFamilyMember(unrelatedChild, unrelatedFamily, ManagerType.PRIMARY);

        Senior seniorProfile = seniorRepository.save(Senior.builder()
                .name("시니어")
                .birth(LocalDate.of(1950, 1, 1))
                .phoneNumber("01012345678")
                .family(family)
                .registeredBy(child)
                .parentUser(senior)
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
        assertThat(notificationRepository.findAll().get(0).getReceiverUser().getUsersId())
                .isEqualTo(child.getUsersId());
        assertThat(notificationRepository.findAll().get(0).getEvent().getSenior().getSeniorId())
                .isEqualTo(seniorProfile.getSeniorId());
        assertThat(notificationRepository.findAll().get(0).getReceiverUser().getUsersId())
                .isNotEqualTo(unrelatedChild.getUsersId());

        // 기기 토큰이 있어도 외부 트랜잭션의 커밋 이전에는 발송하지 않는다.
        assertThat(response.getReceiverCount()).isEqualTo(1);
        double undeliveredAfter = counterValueOrZero("sos_dispatch_total", "result", "undelivered");
        assertThat(undeliveredAfter - undeliveredBefore).isZero();

        double eventSuccessAfter = counterValueOrZero("sos_event_total", "result", "success");
        assertThat(eventSuccessAfter - eventSuccessBefore).isZero();

        double notDeliveredAfter = sumNotDeliveredFcmSendCounters();
        assertThat(notDeliveredAfter - notDeliveredBefore).isZero();
    }

    @Test
    void notificationListContainsOnlyEventsForSelectedSenior() {
        Family familyA = familyRepository.save(Family.builder()
                .seniorCode("SOS-FILTER-" + System.nanoTime())
                .build());
        Family familyB = familyRepository.save(Family.builder()
                .seniorCode("SOS-FILTER-B-" + System.nanoTime())
                .build());
        User child = saveUser("filter-child", "자녀", Role.CHILD);
        User parentA = saveUser("filter-parent-a", "시니어A", Role.PARENT);
        User parentB = saveUser("filter-parent-b", "시니어B", Role.PARENT);
        addFamilyMember(child, familyA, ManagerType.PRIMARY);
        addFamilyMember(child, familyB, ManagerType.PRIMARY);
        addFamilyMember(parentA, familyA, ManagerType.NONE);
        addFamilyMember(parentB, familyB, ManagerType.NONE);
        Senior seniorA = saveSenior("시니어A", familyA, child, parentA, "01011111111");
        Senior seniorB = saveSenior("시니어B", familyB, child, parentB, "01022222222");
        entityManager.flush();

        SosEventResponse eventA = eventService.createSosEvent(parentA, sosRequest());
        SosEventResponse eventB = eventService.createSosEvent(parentB, sosRequest());
        assertThat(eventA.getSeniorId()).isEqualTo(seniorA.getSeniorId());
        assertThat(eventB.getSeniorId()).isEqualTo(seniorB.getSeniorId());
        assertThat(eventA.getNotificationStatus().name()).isEqualTo("NOT_DISPATCHED");
        assertThat(eventA.getReason()).isEqualTo("NO_DEVICE_TOKEN");
        assertThat(eventA.getReceiverCount()).isEqualTo(1);

        NotificationListResponse response = notificationService.getNotificationList(
                child.getUsersId(), seniorA.getSeniorId(), NotificationType.SOS, null, 20);

        assertThat(response.getItems()).extracting(NotificationListResponse.NotificationItem::getEventId)
                .containsExactly(eventA.getId())
                .doesNotContain(eventB.getId());
    }

    private User saveUser(String loginIdPrefix, String name, Role role) {
        return userRepository.save(User.builder()
                .loginId(loginIdPrefix + "-" + System.nanoTime())
                .name(name)
                .role(role)
                .build());
    }

    private Senior saveSenior(
            String name, Family family, User registeredBy, User parentUser, String phoneNumber) {
        return seniorRepository.save(Senior.builder()
                .name(name)
                .birth(LocalDate.of(1950, 1, 1))
                .phoneNumber(phoneNumber)
                .family(family)
                .registeredBy(registeredBy)
                .parentUser(parentUser)
                .build());
    }

    private void addFamilyMember(User user, Family family, ManagerType managerType) {
        familyMemberRepository.save(FamilyMember.builder()
                .user(user)
                .family(family)
                .managerType(managerType)
                .build());
        user.updateFamily(family);
        user.updateManagerType(managerType);
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
