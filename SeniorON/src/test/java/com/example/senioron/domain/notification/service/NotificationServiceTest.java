package com.example.senioron.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.BDDMockito.given;

import com.example.senioron.domain.device.entity.Device;
import com.example.senioron.domain.device.entity.DeviceStatus;
import com.example.senioron.domain.device.repository.DeviceRepository;
import com.example.senioron.domain.event.util.FcmSender;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.notification.dto.response.NotificationHomeListResponse;
import com.example.senioron.domain.notification.dto.response.ParentDeviceStatusResponse;
import com.example.senioron.domain.notification.entity.NotificationSetting;
import com.example.senioron.domain.notification.entity.NotificationSettingType;
import com.example.senioron.domain.notification.entity.NotificationType;
import com.example.senioron.domain.notification.repository.NotificationRepository;
import com.example.senioron.domain.notification.repository.NotificationSettingRepository;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationServiceTest {

    private static final Long CHILD_ID = 1L;
    private static final Long PARENT_A_ID = 2L;
    private static final Long CHILD_B_ID = 4L;

    private final NotificationRepository notificationRepository = org.mockito.Mockito.mock(NotificationRepository.class);
    private final NotificationSettingRepository notificationSettingRepository = org.mockito.Mockito.mock(NotificationSettingRepository.class);
    private final UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
    private final DeviceRepository deviceRepository = org.mockito.Mockito.mock(DeviceRepository.class);
    private final FcmSender fcmSender = org.mockito.Mockito.mock(FcmSender.class);

    private NotificationService notificationService;
    private Family family;
    private User child;
    private User childB;
    private User parentA;
    private User parentB;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
                notificationRepository, notificationSettingRepository, userRepository, deviceRepository, fcmSender);

        family = Family.builder().familyId(10L).build();
        child = User.builder().usersId(CHILD_ID).family(family).role(Role.CHILD).build();
        childB = User.builder().usersId(CHILD_B_ID).family(family).role(Role.CHILD).build();
        parentA = User.builder().usersId(PARENT_A_ID).family(family).role(Role.PARENT).build();
        parentB = User.builder().usersId(3L).family(family).role(Role.PARENT).build();

        given(userRepository.findById(CHILD_ID)).willReturn(Optional.of(child));
        // 알림 설정은 유저 개인이 아닌 그 가족의 시니어(parentA) 기준으로 저장된다.
        given(notificationSettingRepository.findById(PARENT_A_ID))
                .willReturn(Optional.of(NotificationSetting.builder().user(parentA).build()));
    }

    // 부모가 여러 명이고, 그중 한 명의 기기만 OFFLINE인 경우에도 놓치지 않고 차단해야 한다.
    @Test
    void blocksSettingChangeWhenAnyParentDeviceIsOfflineAmongMultipleParents() {
        given(userRepository.findByFamilyAndUsersIdNotAndRole(family, CHILD_ID, Role.PARENT))
                .willReturn(List.of(parentA, parentB));
        given(deviceRepository.findAllByUserIn(List.of(parentA, parentB))).willReturn(List.of(
                Device.builder().connectionStatus(DeviceStatus.ONLINE).build(),
                Device.builder().connectionStatus(DeviceStatus.OFFLINE).build()
        ));

        assertThatThrownBy(() -> notificationService.updateSetting(CHILD_ID, NotificationSettingType.INACTIVITY, true))
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.PARENT_DEVICE_OFFLINE);
    }

    // 한 명의 부모가 기기를 여러 대 갖고 있고, 그중 하나만 OFFLINE이어도 차단해야 한다.
    @Test
    void blocksSettingChangeWhenOneOfMultipleDevicesOfSameParentIsOffline() {
        given(userRepository.findByFamilyAndUsersIdNotAndRole(family, CHILD_ID, Role.PARENT))
                .willReturn(List.of(parentA));
        given(deviceRepository.findAllByUserIn(List.of(parentA))).willReturn(List.of(
                Device.builder().connectionStatus(DeviceStatus.ONLINE).build(),
                Device.builder().connectionStatus(DeviceStatus.OFFLINE).build()
        ));

        assertThatThrownBy(() -> notificationService.updateSetting(CHILD_ID, NotificationSettingType.INACTIVITY, true))
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.PARENT_DEVICE_OFFLINE);
    }

    @Test
    void allowsSettingChangeWhenAllParentDevicesAreOnline() {
        given(userRepository.findByFamilyAndUsersIdNotAndRole(family, CHILD_ID, Role.PARENT))
                .willReturn(List.of(parentA, parentB));
        given(deviceRepository.findAllByUserIn(List.of(parentA, parentB))).willReturn(List.of(
                Device.builder().connectionStatus(DeviceStatus.ONLINE).build(),
                Device.builder().connectionStatus(DeviceStatus.ONLINE).build()
        ));

        assertThat(notificationService.updateSetting(CHILD_ID, NotificationSettingType.INACTIVITY, true)).isNotNull();
    }

    @Test
    void allowsSettingChangeWhenParentHasNoDeviceRecord() {
        given(userRepository.findByFamilyAndUsersIdNotAndRole(family, CHILD_ID, Role.PARENT))
                .willReturn(List.of(parentA));
        given(deviceRepository.findAllByUserIn(List.of(parentA))).willReturn(List.of());

        assertThat(notificationService.updateSetting(CHILD_ID, NotificationSettingType.INACTIVITY, true)).isNotNull();
    }

    // 가족이 없으면 설정을 공유할 시니어를 특정할 수 없으므로 변경이 거부되어야 한다.
    @Test
    void rejectsSettingChangeWhenChildHasNoFamily() {
        User lonelyChild = User.builder().usersId(CHILD_ID).family(null).role(Role.CHILD).build();
        given(userRepository.findById(CHILD_ID)).willReturn(Optional.of(lonelyChild));

        assertThatThrownBy(() -> notificationService.updateSetting(CHILD_ID, NotificationSettingType.INACTIVITY, true))
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.FAMILY_NOT_FOUND);
    }

    // 같은 가족에 부모가 없으면(비정상 상태) 마찬가지로 거부되어야 한다.
    @Test
    void rejectsSettingChangeWhenNoParentExistsInFamily() {
        given(userRepository.findByFamilyAndUsersIdNotAndRole(family, CHILD_ID, Role.PARENT))
                .willReturn(List.of());

        assertThatThrownBy(() -> notificationService.updateSetting(CHILD_ID, NotificationSettingType.INACTIVITY, true))
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.FAMILY_NOT_FOUND);
    }

    // 자녀가 설정을 바꾸면 그 가족의 시니어(부모) 행에 저장되어야 한다 (본인 행이 아님).
    @Test
    void childUpdateWritesToSeniorsSetting() {
        given(userRepository.findByFamilyAndUsersIdNotAndRole(family, CHILD_ID, Role.PARENT))
                .willReturn(List.of(parentA));
        given(deviceRepository.findAllByUserIn(List.of(parentA))).willReturn(List.of());

        notificationService.updateSetting(CHILD_ID, NotificationSettingType.RISK_LINK, false);

        org.mockito.Mockito.verify(notificationSettingRepository).findById(PARENT_A_ID);
    }

    // 부모가 직접 호출하면 자기 자신의 설정 행을 바로 갱신해야 한다.
    @Test
    void parentUpdatesOwnSettingDirectly() {
        given(userRepository.findById(PARENT_A_ID)).willReturn(Optional.of(parentA));
        given(deviceRepository.findAllByUserIn(List.of())).willReturn(List.of());

        assertThat(notificationService.updateSetting(PARENT_A_ID, NotificationSettingType.OUTING_RETURN, false)).isNotNull();
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

    @Test
    void parentDeviceStatusIsOnlineOnlyWhenAllDevicesAreOnline() {
        given(userRepository.findByFamilyAndUsersIdNotAndRole(family, CHILD_ID, Role.PARENT))
                .willReturn(List.of(parentA, parentB));
        given(deviceRepository.findAllByUserIn(List.of(parentA, parentB))).willReturn(List.of(
                Device.builder().connectionStatus(DeviceStatus.ONLINE).build(),
                Device.builder().connectionStatus(DeviceStatus.OFFLINE).build()
        ));

        ParentDeviceStatusResponse response = notificationService.getParentDeviceStatus(CHILD_ID);

        assertThat(response.isOnline()).isFalse();
    }

    @Test
    void parentDeviceStatusIsOfflineWhenNoDeviceIsKnown() {
        given(userRepository.findByFamilyAndUsersIdNotAndRole(family, CHILD_ID, Role.PARENT))
                .willReturn(List.of());

        ParentDeviceStatusResponse response = notificationService.getParentDeviceStatus(CHILD_ID);

        assertThat(response.isOnline()).isFalse();
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

        NotificationHomeListResponse response = notificationService.getHomeSettings(CHILD_ID);

        assertThat(response.getEnabledCount()).isEqualTo(4);
    }
}
