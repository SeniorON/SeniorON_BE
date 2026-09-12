package com.example.senioron.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.senioron.domain.device.entity.Device;
import com.example.senioron.domain.device.entity.DeviceStatus;
import com.example.senioron.domain.device.repository.DeviceRepository;
import com.example.senioron.domain.event.util.FcmSender;
import com.example.senioron.domain.event.entity.Event;
import com.example.senioron.domain.event.entity.EventType;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.notification.dto.NotificationDispatchTarget;
import com.example.senioron.domain.notification.dto.response.NotificationHomeListResponse;
import com.example.senioron.domain.notification.dto.response.NotificationHomeResponse;
import com.example.senioron.domain.notification.dto.response.NotificationListResponse;
import com.example.senioron.domain.notification.dto.response.ParentDeviceStatusResponse;
import com.example.senioron.domain.notification.entity.Notification;
import com.example.senioron.domain.notification.entity.NotificationSetting;
import com.example.senioron.domain.notification.entity.NotificationSettingType;
import com.example.senioron.domain.notification.entity.NotificationType;
import com.example.senioron.domain.notification.repository.NotificationRepository;
import com.example.senioron.domain.notification.repository.NotificationSettingRepository;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.entity.SeniorRelation;
import com.example.senioron.domain.senior.entity.UserSenior;
import com.example.senioron.domain.senior.repository.UserSeniorRepository;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class NotificationServiceTest {

    private static final Long CHILD_ID = 1L;
    private static final Long PARENT_A_ID = 2L;
    private static final Long CHILD_B_ID = 4L;
    private static final Long SENIOR_A_ID = 11L;

    private final NotificationRepository notificationRepository = org.mockito.Mockito.mock(NotificationRepository.class);
    private final NotificationSettingRepository notificationSettingRepository = org.mockito.Mockito.mock(NotificationSettingRepository.class);
    private final UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
    private final UserSeniorRepository userSeniorRepository = org.mockito.Mockito.mock(UserSeniorRepository.class);
    private final DeviceRepository deviceRepository = org.mockito.Mockito.mock(DeviceRepository.class);
    private final FcmSender fcmSender = org.mockito.Mockito.mock(FcmSender.class);
    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private NotificationService notificationService;
    private Family family;
    private User child;
    private User childB;
    private User parentA;
    private User parentB;
    private Senior seniorA;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
                notificationRepository, notificationSettingRepository, userRepository, userSeniorRepository,
                deviceRepository, fcmSender, meterRegistry);

        family = Family.builder().familyId(10L).build();
        child = User.builder().usersId(CHILD_ID).family(family).role(Role.CHILD).build();
        childB = User.builder().usersId(CHILD_B_ID).family(family).role(Role.CHILD).build();
        parentA = User.builder().usersId(PARENT_A_ID).family(family).role(Role.PARENT).build();
        parentB = User.builder().usersId(3L).family(family).role(Role.PARENT).build();
        seniorA = Senior.builder().seniorId(SENIOR_A_ID).family(family).registeredBy(child)
                .parentUser(parentA).build();

        given(userRepository.findById(CHILD_ID)).willReturn(Optional.of(child));
        given(userSeniorRepository.findByUserAndSenior_SeniorId(child, SENIOR_A_ID))
                .willReturn(Optional.of(UserSenior.builder().user(child).senior(seniorA)
                        .relation(SeniorRelation.MOTHER).build()));
        // 알림 설정은 유저 개인이 아닌 그 가족의 시니어(parentA) 기준으로 저장된다.
        given(notificationSettingRepository.findById(PARENT_A_ID))
                .willReturn(Optional.of(NotificationSetting.builder().user(parentA).build()));
    }

    @AfterEach
    void tearDown() {
        notificationService.shutdownDispatchExecutors();
        meterRegistry.close();
    }

    @ParameterizedTest
    @EnumSource(value = EventType.class, names = {"INACTIVITY", "RISK_LINK", "OUTING_RETURN"})
    void sendsUsingActualParentSettingEvenWhenFirstParentDisabled(EventType type) {
        assertActualParentSetting(type, true);
    }

    @ParameterizedTest
    @EnumSource(value = EventType.class, names = {"INACTIVITY", "RISK_LINK", "OUTING_RETURN"})
    void suppressesUsingActualParentSettingEvenWhenFirstParentEnabled(EventType type) {
        assertActualParentSetting(type, false);
    }

    private void assertActualParentSetting(EventType type, boolean enabled) {
        given(userRepository.findByFamilyAndUsersIdNotAndRole(family, parentB.getUsersId(), Role.CHILD))
                .willReturn(List.of(child, childB));
        given(userRepository.findByFamilyAndUsersIdNotAndRole(family, CHILD_ID, Role.PARENT))
                .willReturn(List.of(parentA, parentB));
        given(notificationSettingRepository.findById(PARENT_A_ID)).willReturn(Optional.of(
                settingFor(parentA, !enabled)));
        given(notificationSettingRepository.findById(parentB.getUsersId())).willReturn(Optional.of(
                settingFor(parentB, enabled)));
        Event event = Event.builder().eventId(100L).user(parentB).triggeredUser(parentB).eventType(type).build();

        TransactionSynchronizationManager.initSynchronization();
        try {
            notificationService.createFormEvent(event);
            if (enabled) {
                org.mockito.Mockito.verify(notificationRepository).saveAll(org.mockito.ArgumentMatchers.argThat(items -> {
                    var saved = new java.util.ArrayList<Notification>();
                    items.forEach(saved::add);
                    return saved.size() == 2 && saved.stream().allMatch(n -> n.getSendUser() == parentB);
                }));
                assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
            } else {
                org.mockito.Mockito.verifyNoInteractions(notificationRepository, deviceRepository, fcmSender);
                assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
            }
            org.mockito.Mockito.verify(notificationSettingRepository).findById(parentB.getUsersId());
            org.mockito.Mockito.verify(notificationSettingRepository, org.mockito.Mockito.never()).findById(PARENT_A_ID);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void sosStillIgnoresActualParentDisabledSettings() {
        given(userRepository.findByFamilyAndUsersIdNotAndRole(family, parentB.getUsersId(), Role.CHILD))
                .willReturn(List.of(child));
        given(notificationSettingRepository.findById(parentB.getUsersId()))
                .willReturn(Optional.of(settingFor(parentB, false)));
        var targets = notificationService.prepareSosNotifications(Event.builder()
                .eventId(100L).user(parentB).triggeredUser(parentB).eventType(EventType.SOS).build());
        assertThat(targets).hasSize(1);
        org.mockito.Mockito.verifyNoInteractions(notificationSettingRepository);
    }

    private NotificationSetting settingFor(User parent, boolean enabled) {
        return NotificationSetting.builder().user(parent).inactivityEnabled(enabled)
                .riskLinkEnabled(enabled).outingReturnEnabled(enabled).build();
    }

    // 다른 부모 계정의 상태와 무관하게 선택한 시니어의 부모 기기만 확인한다.
    @Test
    void allowsSettingChangeWhenSelectedSeniorParentHasOnlineDevice() {
        given(deviceRepository.findAllByUserIn(List.of(parentA))).willReturn(List.of(
                Device.builder().connectionStatus(DeviceStatus.ONLINE).build(),
                Device.builder().connectionStatus(DeviceStatus.OFFLINE).build()
        ));

        assertThat(notificationService.updateSetting(CHILD_ID, SENIOR_A_ID, NotificationSettingType.INACTIVITY, true)).isNotNull();
    }

    // 한 명의 부모가 기기를 여러 대 갖고 있어도, 그중 하나만 ONLINE이면 허용해야 한다 (예전에 쓰던 기기가 방치돼 있어도 막히면 안 됨).
    @Test
    void allowsSettingChangeWhenAtLeastOneDeviceOfSameParentIsOnline() {
        given(deviceRepository.findAllByUserIn(List.of(parentA))).willReturn(List.of(
                Device.builder().connectionStatus(DeviceStatus.ONLINE).build(),
                Device.builder().connectionStatus(DeviceStatus.OFFLINE).build()
        ));

        assertThat(notificationService.updateSetting(CHILD_ID, SENIOR_A_ID, NotificationSettingType.INACTIVITY, true)).isNotNull();
    }

    // 부모님 기기가 전부 OFFLINE이면(연락 닿을 방법이 없으면) 그때는 여전히 차단해야 한다.
    @Test
    void blocksSettingChangeWhenAllParentDevicesAreOffline() {
        given(deviceRepository.findAllByUserIn(List.of(parentA))).willReturn(List.of(
                Device.builder().connectionStatus(DeviceStatus.OFFLINE).build(),
                Device.builder().connectionStatus(DeviceStatus.OFFLINE).build()
        ));

        assertThatThrownBy(() -> notificationService.updateSetting(CHILD_ID, SENIOR_A_ID, NotificationSettingType.INACTIVITY, true))
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.PARENT_DEVICE_OFFLINE);
    }

    @Test
    void ignoresAnotherParentsOnlineDeviceWhenSelectedSeniorParentIsOffline() {
        given(deviceRepository.findAllByUserIn(List.of(parentA))).willReturn(List.of(
                Device.builder().connectionStatus(DeviceStatus.OFFLINE).build()));
        given(deviceRepository.findAllByUserIn(List.of(parentB))).willReturn(List.of(
                Device.builder().connectionStatus(DeviceStatus.ONLINE).build()));

        assertThatThrownBy(() -> notificationService.updateSetting(
                CHILD_ID, SENIOR_A_ID, NotificationSettingType.INACTIVITY, true))
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.PARENT_DEVICE_OFFLINE);

        verify(deviceRepository, never()).findAllByUserIn(List.of(parentB));
    }

    @Test
    void allowsSettingChangeWhenAllParentDevicesAreOnline() {
        given(deviceRepository.findAllByUserIn(List.of(parentA))).willReturn(List.of(
                Device.builder().connectionStatus(DeviceStatus.ONLINE).build(),
                Device.builder().connectionStatus(DeviceStatus.ONLINE).build()
        ));

        assertThat(notificationService.updateSetting(CHILD_ID, SENIOR_A_ID, NotificationSettingType.INACTIVITY, true)).isNotNull();
    }

    @Test
    void blocksSettingChangeWhenParentHasNoDeviceRecord() {
        given(deviceRepository.findAllByUserIn(List.of(parentA))).willReturn(List.of());

        assertThatThrownBy(() -> notificationService.updateSetting(CHILD_ID, SENIOR_A_ID, NotificationSettingType.INACTIVITY, true))
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.PARENT_DEVICE_OFFLINE);
    }

    // 요청한 시니어가 자녀의 관리 대상이 아니면 변경할 수 없다.
    @Test
    void rejectsSettingChangeWithoutManagedSeniorRelation() {
        given(userSeniorRepository.findByUserAndSenior_SeniorId(child, SENIOR_A_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.updateSetting(CHILD_ID, SENIOR_A_ID, NotificationSettingType.INACTIVITY, true))
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.SENIOR_MANAGEMENT_ACCESS_DENIED);
    }

    // 선택한 시니어에 실제 부모 계정이 아직 연결되지 않았다면 설정을 저장할 수 없다.
    @Test
    void rejectsSettingChangeWhenSelectedSeniorHasNoParentAccount() {
        Senior unlinkedSenior = Senior.builder().seniorId(SENIOR_A_ID).family(family)
                .registeredBy(child).build();
        given(userSeniorRepository.findByUserAndSenior_SeniorId(child, SENIOR_A_ID))
                .willReturn(Optional.of(UserSenior.builder().user(child).senior(unlinkedSenior)
                        .relation(SeniorRelation.MOTHER).build()));

        assertThatThrownBy(() -> notificationService.updateSetting(CHILD_ID, SENIOR_A_ID, NotificationSettingType.INACTIVITY, true))
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.SENIOR_PARENT_USER_NOT_FOUND);
    }

    // 자녀가 설정을 바꾸면 그 가족의 시니어(부모) 행에 저장되어야 한다 (본인 행이 아님).
    @Test
    void childUpdateWritesToSeniorsSetting() {
        given(deviceRepository.findAllByUserIn(List.of(parentA))).willReturn(List.of(
                Device.builder().connectionStatus(DeviceStatus.ONLINE).build()
        ));

        notificationService.updateSetting(CHILD_ID, SENIOR_A_ID, NotificationSettingType.RISK_LINK, false);

        org.mockito.Mockito.verify(notificationSettingRepository).findById(PARENT_A_ID);
    }

    // 부모가 직접 호출하면 자기 자신의 설정 행을 바로 갱신해야 한다.
    @Test
    void parentUpdatesOwnSettingDirectly() {
        given(userRepository.findById(PARENT_A_ID)).willReturn(Optional.of(parentA));
        given(deviceRepository.findAllByUserIn(List.of())).willReturn(List.of());

        given(userSeniorRepository.findByUserAndSenior_SeniorId(parentA, SENIOR_A_ID))
                .willReturn(Optional.of(UserSenior.builder().user(parentA).senior(seniorA)
                        .relation(SeniorRelation.MOTHER).build()));
        given(deviceRepository.findAllByUserIn(List.of(parentA))).willReturn(List.of(
                Device.builder().connectionStatus(DeviceStatus.ONLINE).build()));
        assertThat(notificationService.updateSetting(PARENT_A_ID, SENIOR_A_ID, NotificationSettingType.OUTING_RETURN, false)).isNotNull();
        org.mockito.Mockito.verify(notificationSettingRepository).findById(PARENT_A_ID);
    }

    // 같은 가족의 자녀가 둘이면, 같은 시니어 설정을 공유해서 봐야 한다.
    @Test
    void isEnabledIsSharedAcrossChildrenOfTheSameFamily() {
        NotificationSetting sharedSetting = NotificationSetting.builder().user(parentA).build();
        sharedSetting.updateInactivityEnabled(false);
        given(notificationSettingRepository.findById(PARENT_A_ID)).willReturn(Optional.of(sharedSetting));

        given(userRepository.findByFamilyAndUsersIdNotAndRole(family, CHILD_ID, Role.PARENT))
                .willReturn(List.of(parentA));
        given(userRepository.findByFamilyAndUsersIdNotAndRole(family, CHILD_B_ID, Role.PARENT))
                .willReturn(List.of(parentA));

        assertThat(notificationService.isEnabled(child, NotificationType.INACTIVITY)).isFalse();
        assertThat(notificationService.isEnabled(childB, NotificationType.INACTIVITY)).isFalse();
    }

    // 기기가 여러 대라도 그중 하나만 ONLINE이면 온라인으로 봐야 한다 (방치된 기기 하나 때문에 항상 오프라인으로 뜨면 안 됨).
    @Test
    void parentDeviceStatusIsOnlineWhenAtLeastOneDeviceIsOnline() {
        given(deviceRepository.findAllByUserIn(List.of(parentA))).willReturn(List.of(
                Device.builder().connectionStatus(DeviceStatus.ONLINE).build(),
                Device.builder().connectionStatus(DeviceStatus.OFFLINE).build()
        ));

        ParentDeviceStatusResponse response = notificationService.getParentDeviceStatus(CHILD_ID, SENIOR_A_ID);

        assertThat(response.isOnline()).isTrue();
    }

    @Test
    void parentDeviceStatusIsOfflineWhenAllDevicesAreOffline() {
        given(deviceRepository.findAllByUserIn(List.of(parentA))).willReturn(List.of(
                Device.builder().connectionStatus(DeviceStatus.OFFLINE).build()
        ));

        ParentDeviceStatusResponse response = notificationService.getParentDeviceStatus(CHILD_ID, SENIOR_A_ID);

        assertThat(response.isOnline()).isFalse();
    }

    @Test
    void parentDeviceStatusIsOfflineWhenNoDeviceIsKnown() {
        given(deviceRepository.findAllByUserIn(List.of(parentA))).willReturn(List.of());

        ParentDeviceStatusResponse response = notificationService.getParentDeviceStatus(CHILD_ID, SENIOR_A_ID);

        assertThat(response.isOnline()).isFalse();
    }

    @Test
    void parentDeviceStatusIsOfflineWhenSelectedSeniorHasNoLinkedParent() {
        Senior unlinkedSenior = Senior.builder().seniorId(SENIOR_A_ID).family(family)
                .registeredBy(child).build();
        given(userSeniorRepository.findByUserAndSenior_SeniorId(child, SENIOR_A_ID))
                .willReturn(Optional.of(UserSenior.builder().user(child).senior(unlinkedSenior)
                        .relation(SeniorRelation.MOTHER).build()));

        ParentDeviceStatusResponse response = notificationService.getParentDeviceStatus(CHILD_ID, SENIOR_A_ID);

        assertThat(response.isOnline()).isFalse();
        org.mockito.Mockito.verifyNoInteractions(deviceRepository);
    }

    // SOS 알림은 필수 알림이라 개인 설정과 무관하게 항상 발송 대상이어야 한다.
    @Test
    void sosNotificationIsAlwaysEnabledRegardlessOfSetting() {
        assertThat(notificationService.isEnabled(child, NotificationType.SOS)).isTrue();
    }

    @Test
    void getHomeSettingsReflectsSeniorsSetting() {
        given(userRepository.findByFamilyAndUsersIdNotAndRole(family, CHILD_ID, Role.PARENT))
                .willReturn(List.of(parentA));

        NotificationHomeListResponse response = notificationService.getHomeSettings(CHILD_ID, SENIOR_A_ID);

        assertThat(response.getEnabledCount()).isEqualTo(4);
    }

    // 타입별로 따로 조회하지 않고 한 번에 받아온 목록에서, 자바에서 타입별 최신 1건만 골라야 한다.
    @Test
    void getHomeSettingsPicksMostRecentNotificationPerType() {
        given(userRepository.findByFamilyAndUsersIdNotAndRole(family, CHILD_ID, Role.PARENT))
                .willReturn(List.of(parentA));

        Notification olderInactivity = notificationAt(NotificationType.INACTIVITY, "예전 무활동 알림", LocalDateTime.now().minusHours(2));
        Notification newerInactivity = notificationAt(NotificationType.INACTIVITY, "최신 무활동 알림", LocalDateTime.now().minusMinutes(10));
        Notification sosNotification = notificationAt(NotificationType.SOS, "SOS", LocalDateTime.now().minusMinutes(5));

        // 리포지토리는 ORDER BY createdAt DESC로 정렬된 결과를 준다 — 그 순서 그대로 스텁한다.
        given(notificationRepository.findLatestUnreadByTypes(
                org.mockito.ArgumentMatchers.eq(CHILD_ID),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .willReturn(List.of(newerInactivity, sosNotification, olderInactivity));

        NotificationHomeListResponse response = notificationService.getHomeSettings(CHILD_ID, SENIOR_A_ID);

        NotificationHomeResponse inactivityCard = response.getItems().stream()
                .filter(item -> item.getType() == NotificationType.INACTIVITY)
                .findFirst()
                .orElseThrow();
        assertThat(inactivityCard.getSummary()).isEqualTo("최신 무활동 알림");
    }

    private Notification notificationAt(NotificationType type, String body, LocalDateTime createdAt) {
        Notification notification = Notification.builder()
                .sendUser(parentA)
                .receiverUser(child)
                .notificationType(type)
                .title(type.name())
                .body(body)
                .isRead(false)
                .build();
        ReflectionTestUtils.setField(notification, "createdAt", createdAt);
        return notification;
    }

    // 첫 페이지(cursor 없음)에서만 전체 개수를 세고, 다음 페이지("더보기")에서는 다시 세지 않아야 한다.
    @Test
    void getNotificationListOnlyCountsTotalOnFirstPage() {
        given(notificationRepository.findByTypeWithCursor(
                org.mockito.ArgumentMatchers.eq(CHILD_ID),
                org.mockito.ArgumentMatchers.eq(NotificationType.SOS),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .willReturn(List.of());
        given(notificationRepository.countByTypeWithin30Days(
                org.mockito.ArgumentMatchers.eq(CHILD_ID),
                org.mockito.ArgumentMatchers.eq(NotificationType.SOS),
                org.mockito.ArgumentMatchers.any()))
                .willReturn(7L);

        NotificationListResponse firstPage = notificationService.getNotificationList(CHILD_ID, NotificationType.SOS, null, 20);
        NotificationListResponse secondPage = notificationService.getNotificationList(CHILD_ID, NotificationType.SOS, 100L, 20);

        assertThat(firstPage.getTotalCount()).isEqualTo(7L);
        assertThat(secondPage.getTotalCount()).isNull();
        org.mockito.Mockito.verify(notificationRepository, org.mockito.Mockito.times(1))
                .countByTypeWithin30Days(
                        org.mockito.ArgumentMatchers.eq(CHILD_ID),
                        org.mockito.ArgumentMatchers.eq(NotificationType.SOS),
                        org.mockito.ArgumentMatchers.any());
    }

    // 조회 개수가 범위(1~50) 밖이면 구체적인 코드로 거부해야 한다.
    @Test
    void rejectsOutOfRangeSizeWithSpecificErrorCode() {
        assertThatThrownBy(() -> notificationService.getNotificationList(CHILD_ID, NotificationType.SOS, null, 0))
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.NOTIFICATION_SIZE_OUT_OF_RANGE);

        assertThatThrownBy(() -> notificationService.getNotificationList(CHILD_ID, NotificationType.SOS, null, 51))
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.NOTIFICATION_SIZE_OUT_OF_RANGE);
    }

    // 본인이 받은 알림이 아니면 읽음처리/삭제 둘 다 구체적인 코드로 거부해야 한다.
    @Test
    void rejectsReadAndDeleteOnSomeoneElsesNotificationWithSpecificErrorCode() {
        Notification othersNotification = notificationAt(NotificationType.SOS, "다른 사람 알림", LocalDateTime.now());
        ReflectionTestUtils.setField(othersNotification, "receiverUser", parentA);
        given(notificationRepository.findById(1L)).willReturn(java.util.Optional.of(othersNotification));

        assertThatThrownBy(() -> notificationService.markAsRead(CHILD_ID, 1L))
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.NOTIFICATION_ACCESS_DENIED);

        assertThatThrownBy(() -> notificationService.deleteNotification(CHILD_ID, 1L))
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.NOTIFICATION_ACCESS_DENIED);
    }

    // 30일이 지난 알림을 생성 시각 기준으로 일괄 삭제해야 한다.
    @Test
    void deleteOldNotificationsDeletesByThirtyDayThreshold() {
        var thresholdCaptor = org.mockito.ArgumentCaptor.forClass(java.time.LocalDateTime.class);
        given(notificationRepository.deleteAllByCreatedAtBefore(thresholdCaptor.capture())).willReturn(2);

        notificationService.deleteOldNotifications();

        java.time.LocalDateTime captured = thresholdCaptor.getValue();
        java.time.LocalDateTime expected = java.time.LocalDateTime.now().minusDays(30);
        assertThat(java.time.Duration.between(captured, expected).abs()).isLessThan(java.time.Duration.ofSeconds(5));
    }

    // "가장 느린 발송 1건" 수준에 그쳐야 한다 — 발송 1건에 300ms가 걸리는 상황을 흉내
    @Test
    void dispatchSosStartsMultipleReceiversWithoutWaiting() throws Exception {
        long perCallDelayMillis = 300;
        given(fcmSender.sendHighPriority(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong()))
                .willAnswer(invocation -> {
                    Thread.sleep(perCallDelayMillis);
                    return true;
                });

        List<NotificationDispatchTarget> targets = List.of(
                new NotificationDispatchTarget(1L, "SOS", "도움이 필요해요", 100L, List.of("token-1")),
                new NotificationDispatchTarget(2L, "SOS", "도움이 필요해요", 100L, List.of("token-2")),
                new NotificationDispatchTarget(3L, "SOS", "도움이 필요해요", 100L, List.of("token-3")),
                new NotificationDispatchTarget(4L, "SOS", "도움이 필요해요", 100L, List.of("token-4"))
        );

        long startedAt = System.nanoTime();
        notificationService.dispatchSosAsync(targets);
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;

        assertThat(elapsedMillis).isLessThan(perCallDelayMillis);
        org.mockito.Mockito.verify(fcmSender, org.mockito.Mockito.timeout(2000).times(4))
                .sendHighPriority(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong());
    }
}
