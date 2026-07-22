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
import com.example.senioron.domain.notification.dto.response.ParentDeviceStatusResponse;
import com.example.senioron.domain.notification.entity.NotificationSetting;
import com.example.senioron.domain.notification.entity.NotificationSettingType;
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

    private final NotificationRepository notificationRepository = org.mockito.Mockito.mock(NotificationRepository.class);
    private final NotificationSettingRepository notificationSettingRepository = org.mockito.Mockito.mock(NotificationSettingRepository.class);
    private final UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
    private final DeviceRepository deviceRepository = org.mockito.Mockito.mock(DeviceRepository.class);
    private final FcmSender fcmSender = org.mockito.Mockito.mock(FcmSender.class);

    private NotificationService notificationService;
    private Family family;
    private User child;
    private User parentA;
    private User parentB;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
                notificationRepository, notificationSettingRepository, userRepository, deviceRepository, fcmSender);

        family = Family.builder().familyId(10L).build();
        child = User.builder().usersId(CHILD_ID).family(family).role(Role.CHILD).build();
        parentA = User.builder().usersId(2L).family(family).role(Role.PARENT).build();
        parentB = User.builder().usersId(3L).family(family).role(Role.PARENT).build();

        given(userRepository.findById(CHILD_ID)).willReturn(Optional.of(child));
        given(notificationSettingRepository.findById(CHILD_ID))
                .willReturn(Optional.of(NotificationSetting.builder().user(child).build()));
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

        assertThatThrownBy(() -> notificationService.updateSetting(CHILD_ID, NotificationSettingType.SOS, true))
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

        assertThatThrownBy(() -> notificationService.updateSetting(CHILD_ID, NotificationSettingType.SOS, true))
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

        assertThat(notificationService.updateSetting(CHILD_ID, NotificationSettingType.SOS, true)).isNotNull();
    }

    @Test
    void allowsSettingChangeWhenParentHasNoDeviceRecord() {
        given(userRepository.findByFamilyAndUsersIdNotAndRole(family, CHILD_ID, Role.PARENT))
                .willReturn(List.of(parentA));
        given(deviceRepository.findAllByUserIn(List.of(parentA))).willReturn(List.of());

        assertThat(notificationService.updateSetting(CHILD_ID, NotificationSettingType.SOS, true)).isNotNull();
    }

    @Test
    void allowsSettingChangeWhenChildHasNoFamily() {
        User lonelyChild = User.builder().usersId(CHILD_ID).family(null).role(Role.CHILD).build();
        given(userRepository.findById(CHILD_ID)).willReturn(Optional.of(lonelyChild));
        given(notificationSettingRepository.findById(CHILD_ID))
                .willReturn(Optional.of(NotificationSetting.builder().user(lonelyChild).build()));

        assertThat(notificationService.updateSetting(CHILD_ID, NotificationSettingType.SOS, true)).isNotNull();
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
}
