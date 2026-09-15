package com.example.senioron.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.example.senioron.domain.device.entity.Device;
import com.example.senioron.domain.device.entity.DeviceStatus;
import com.example.senioron.domain.device.repository.DeviceRepository;
import com.example.senioron.domain.event.entity.Event;
import com.example.senioron.domain.event.entity.EventType;
import com.example.senioron.domain.event.util.FcmSender;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyMember;
import com.example.senioron.domain.family.repository.FamilyMemberRepository;
import com.example.senioron.domain.notification.dto.response.ParentDeviceStatusResponse;
import com.example.senioron.domain.notification.entity.Notification;
import com.example.senioron.domain.notification.entity.NotificationSetting;
import com.example.senioron.domain.notification.entity.NotificationSettingType;
import com.example.senioron.domain.notification.entity.NotificationType;
import com.example.senioron.domain.notification.repository.NotificationRepository;
import com.example.senioron.domain.notification.repository.NotificationSettingRepository;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.repository.SeniorRepository;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class NotificationServiceTest {

    private static final Long CHILD_ID = 1L;
    private static final Long PARENT_ID = 2L;
    private static final Long SENIOR_ID = 11L;

    private final NotificationRepository notificationRepository =
            org.mockito.Mockito.mock(NotificationRepository.class);
    private final NotificationSettingRepository notificationSettingRepository =
            org.mockito.Mockito.mock(NotificationSettingRepository.class);
    private final UserRepository userRepository =
            org.mockito.Mockito.mock(UserRepository.class);
    private final SeniorRepository seniorRepository =
            org.mockito.Mockito.mock(SeniorRepository.class);
    private final FamilyMemberRepository familyMemberRepository =
            org.mockito.Mockito.mock(FamilyMemberRepository.class);
    private final DeviceRepository deviceRepository =
            org.mockito.Mockito.mock(DeviceRepository.class);
    private final FcmSender fcmSender =
            org.mockito.Mockito.mock(FcmSender.class);
    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private NotificationService notificationService;
    private Family family;
    private User child;
    private User parent;
    private Senior senior;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
                notificationRepository,
                notificationSettingRepository,
                userRepository,
                seniorRepository,
                familyMemberRepository,
                deviceRepository,
                fcmSender,
                meterRegistry
        );

        family = Family.builder().familyId(10L).build();
        child = User.builder()
                .usersId(CHILD_ID)
                .family(family)
                .role(Role.CHILD)
                .build();
        parent = User.builder()
                .usersId(PARENT_ID)
                .family(family)
                .role(Role.PARENT)
                .build();
        senior = Senior.builder()
                .seniorId(SENIOR_ID)
                .family(family)
                .registeredBy(child)
                .parentUser(parent)
                .build();

        given(userRepository.findById(CHILD_ID)).willReturn(Optional.of(child));
        given(seniorRepository.findById(SENIOR_ID)).willReturn(Optional.of(senior));
        given(familyMemberRepository.existsByUserAndFamily(child, family)).willReturn(true);
        given(notificationSettingRepository.findById(PARENT_ID))
                .willReturn(Optional.of(NotificationSetting.builder()
                        .user(parent)
                        .build()));
    }

    @AfterEach
    void tearDown() {
        notificationService.shutdownDispatchExecutors();
        meterRegistry.close();
    }

    @Test
    void eventWithSeniorSendsToChildrenInSeniorFamily() {
        given(familyMemberRepository.findAllBySeniorIdAndUserRole(SENIOR_ID, Role.CHILD))
                .willReturn(List.of(FamilyMember.builder()
                        .user(child)
                        .family(family)
                        .managerType(ManagerType.NONE)
                        .build()));
        given(deviceRepository.findAllByUserIn(List.of(child))).willReturn(List.of());

        Event event = Event.builder()
                .eventId(100L)
                .user(parent)
                .triggeredUser(parent)
                .senior(senior)
                .eventType(EventType.INACTIVITY)
                .build();

        TransactionSynchronizationManager.initSynchronization();
        try {
            notificationService.createFormEvent(event);

            verify(notificationRepository).saveAll(
                    org.mockito.ArgumentMatchers.argThat(items -> {
                        var saved = new java.util.ArrayList<Notification>();
                        items.forEach(saved::add);
                        return saved.size() == 1
                                && saved.get(0).getReceiverUser() == child;
                    })
            );
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void eventSendsToAllChildrenRegardlessOfManagerType() {
        User primary = User.builder().usersId(3L).role(Role.CHILD).build();
        User sub = User.builder().usersId(4L).role(Role.CHILD).build();
        User none = User.builder().usersId(5L).role(Role.CHILD).build();
        List<User> receivers = List.of(primary, sub, none);

        given(familyMemberRepository.findAllBySeniorIdAndUserRole(SENIOR_ID, Role.CHILD))
                .willReturn(List.of(
                        familyMember(primary, ManagerType.PRIMARY),
                        familyMember(sub, ManagerType.SUB),
                        familyMember(none, ManagerType.NONE)
                ));
        given(deviceRepository.findAllByUserIn(receivers)).willReturn(List.of(
                Device.builder().user(primary).deviceToken("primary-token").build(),
                Device.builder().user(sub).deviceToken("sub-token").build(),
                Device.builder().user(none).deviceToken("none-token").build()
        ));

        Event event = Event.builder()
                .eventId(101L)
                .user(parent)
                .triggeredUser(parent)
                .senior(senior)
                .eventType(EventType.SOS)
                .build();

        List<com.example.senioron.domain.notification.dto.NotificationDispatchTarget> targets =
                notificationService.prepareSosNotifications(event);

        assertThat(targets).extracting(
                        com.example.senioron.domain.notification.dto.NotificationDispatchTarget::receiverId)
                .containsExactly(3L, 4L, 5L);
        assertThat(targets).extracting(
                        com.example.senioron.domain.notification.dto.NotificationDispatchTarget::deviceTokens)
                .containsExactly(
                        List.of("primary-token"),
                        List.of("sub-token"),
                        List.of("none-token")
                );
        verify(notificationRepository).saveAll(
                org.mockito.ArgumentMatchers.argThat(items -> {
                    var saved = new java.util.ArrayList<Notification>();
                    items.forEach(saved::add);
                    return saved.size() == 3;
                })
        );
    }

    private FamilyMember familyMember(
            User user,
            ManagerType managerType
    ) {
        return FamilyMember.builder()
                .user(user)
                .family(family)
                .managerType(managerType)
                .build();
    }

    @Test
    void updateSettingUsesSeniorParentSetting() {
        given(deviceRepository.findAllByUserIn(List.of(parent))).willReturn(List.of(
                Device.builder()
                        .connectionStatus(DeviceStatus.ONLINE)
                        .build()
        ));

        var response = notificationService.updateSetting(
                CHILD_ID,
                SENIOR_ID,
                NotificationSettingType.INACTIVITY,
                true
        );

        assertThat(response.getType()).isEqualTo(NotificationSettingType.INACTIVITY);
        assertThat(response.getEnabled()).isTrue();
        verify(notificationSettingRepository).findById(PARENT_ID);
    }

    @Test
    void updateSettingRejectsSeniorFromDifferentFamily() {
        Family otherFamily = Family.builder().familyId(20L).build();
        Senior otherSenior = Senior.builder()
                .seniorId(SENIOR_ID)
                .family(otherFamily)
                .registeredBy(parent)
                .parentUser(parent)
                .build();
        given(seniorRepository.findById(SENIOR_ID)).willReturn(Optional.of(otherSenior));

        assertThatThrownBy(() -> notificationService.updateSetting(
                CHILD_ID,
                SENIOR_ID,
                NotificationSettingType.INACTIVITY,
                true
        ))
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.SENIOR_MANAGEMENT_ACCESS_DENIED);
    }

    @Test
    void parentDeviceStatusUsesSeniorParentDevice() {
        given(deviceRepository.findAllByUserIn(List.of(parent))).willReturn(List.of(
                Device.builder()
                        .connectionStatus(DeviceStatus.ONLINE)
                        .build()
        ));

        ParentDeviceStatusResponse response =
                notificationService.getParentDeviceStatus(CHILD_ID, SENIOR_ID);

        assertThat(response.isOnline()).isTrue();
    }

    @Test
    void getNotificationListRejectsSeniorFromDifferentFamily() {
        Family otherFamily = Family.builder().familyId(20L).build();
        Senior otherSenior = Senior.builder()
                .seniorId(SENIOR_ID)
                .family(otherFamily)
                .registeredBy(parent)
                .parentUser(parent)
                .build();
        given(seniorRepository.findById(SENIOR_ID)).willReturn(Optional.of(otherSenior));

        assertThatThrownBy(() -> notificationService.getNotificationList(
                CHILD_ID,
                SENIOR_ID,
                NotificationType.SOS,
                null,
                20
        ))
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.SENIOR_MANAGEMENT_ACCESS_DENIED);
    }
}
