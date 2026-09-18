package com.example.senioron.domain.device.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.senioron.domain.device.entity.Device;
import com.example.senioron.domain.device.entity.DeviceStatus;
import com.example.senioron.domain.device.repository.DeviceRepository;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyMember;
import com.example.senioron.domain.family.repository.FamilyMemberRepository;
import com.example.senioron.domain.family.repository.FamilyRepository;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.senior.repository.SeniorRepository;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.domain.device.dto.request.DeviceStatusUpdateRequest;
import com.example.senioron.domain.user.entity.ManagerType;
import java.util.UUID;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

/**
 * 같은 기기(device_identifier)에서 부모 로그아웃 후 자녀가 로그인해도
 * 부모의 FCM 토큰이 남아있어 두 계정 모두에게 알림이 가던 버그를 검증한다.
 */
@DataJpaTest
class DeviceServiceTest {

    private static final String DEVICE_IDENTIFIER = "device-A";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private SeniorRepository seniorRepository;

    @Autowired
    private FamilyRepository familyRepository;

    @Autowired
    private FamilyMemberRepository familyMemberRepository;

    private DeviceService deviceService;

    private DeviceService deviceService() {
        if (deviceService == null) {
            deviceService = new DeviceService(
                    deviceRepository,
                    seniorRepository,
                    familyMemberRepository
            );
        }
        return deviceService;
    }

    @Test
    void logoutClearsDeviceTokenButKeepsConnectionStatus() {
        User parent = saveUser("parent", Role.PARENT);

        deviceService().registerToken(parent, "fcm-token-parent", DEVICE_IDENTIFIER);
        deviceService().clearToken(parent, DEVICE_IDENTIFIER);

        Device device = deviceRepository.findByDeviceIdentifier(DEVICE_IDENTIFIER).orElseThrow();

        assertThat(device.getDeviceToken()).isNull();
        assertThat(device.getConnectionStatus()).isEqualTo(DeviceStatus.ONLINE);
    }

    @Test
    void updateDeviceStatusSavesAllDeviceStatusFields() {
        Family family = familyRepository.saveAndFlush(
                Family.builder()
                        .seniorCode("family-" + UUID.randomUUID())
                        .build()
        );

        User parent = saveUser("parent", Role.PARENT, family);

        DeviceStatusUpdateRequest request =
                new DeviceStatusUpdateRequest(
                        DEVICE_IDENTIFIER,
                        "Galaxy S24",
                        72,
                        true,   // charging
                        true,   // deviceStatusSharingEnabled
                        true,   // networkConnected
                        true,   // defaultHomeEnabled
                        true,   // locationPermissionGranted
                        false,  // gpsEnabled
                        true,   // notificationPermissionGranted
                        true    // appExecutionMaintained
                );

        deviceService().updateDeviceStatus(parent, request);

        Device device = deviceRepository
                .findByDeviceIdentifier(DEVICE_IDENTIFIER)
                .orElseThrow();

        assertThat(device.getDeviceName()).isEqualTo("Galaxy S24");
        assertThat(device.getConnectionStatus()).isEqualTo(DeviceStatus.ONLINE);
        assertThat(device.getBatteryLevel()).isEqualTo(72);
        assertThat(device.getCharging()).isTrue();
        assertThat(device.getDeviceStatusSharingEnabled()).isTrue();
        assertThat(device.getNetworkConnected()).isTrue();
        assertThat(device.getDefaultHomeEnabled()).isTrue();
        assertThat(device.getLocationPermissionGranted()).isTrue();
        assertThat(device.getGpsEnabled()).isFalse();
        assertThat(device.getNotificationPermissionGranted()).isTrue();
        assertThat(device.getAppExecutionMaintained()).isTrue();
        assertThat(device.getLastConnectedAt()).isNotNull();
    }



    // 기기 A에서 부모 로그인 → 로그아웃 → 같은 기기 A에서 자녀 로그인.
// 로그아웃 시 FCM 토큰만 제거하고 연결 상태는 유지한다.
// 같은 기기에서 다른 계정이 로그인하면 기기 row의 소유자와 FCM 토큰이
// 새 계정으로 변경되고 연결 상태는 ONLINE을 유지한다.
    @Test
    void reloginWithDifferentAccountOnSameDeviceLeavesOnlyOneActiveTokenOwnedByNewAccount() {
        User parent = saveUser("parent", Role.PARENT);
        User child = saveUser("child", Role.CHILD);

        deviceService().registerToken(parent, "fcm-token-parent", DEVICE_IDENTIFIER);
        deviceService().clearToken(parent, DEVICE_IDENTIFIER);
        deviceService().registerToken(child, "fcm-token-child", DEVICE_IDENTIFIER);

        assertThat(deviceRepository.count()).isEqualTo(1L);

        Device device = deviceRepository.findByDeviceIdentifier(DEVICE_IDENTIFIER).orElseThrow();

        assertThat(device.getUser().getUsersId()).isEqualTo(child.getUsersId());
        assertThat(device.getDeviceToken()).isEqualTo("fcm-token-child");
        assertThat(device.getConnectionStatus())
                .isEqualTo(DeviceStatus.ONLINE);
    }

    @Test
    void reconnectDeviceWithoutRegisteredDeviceDoesNothing() {
        User parent = saveUser("parent", Role.PARENT);

        deviceService().reconnectDevice(parent);

        assertThat(deviceRepository.count()).isZero();
    }

    @Test
    void homeLocationUsesSeniorLinkedToCurrentParentInsteadOfFirstSeniorInFamily() {
        Family family = familyRepository.saveAndFlush(Family.builder()
                .seniorCode("family-" + UUID.randomUUID()).build());
        Family otherFamily = familyRepository.saveAndFlush(Family.builder()
                .seniorCode("family-" + UUID.randomUUID()).build());
        User child = saveUser("child-owner", Role.CHILD, family);
        User currentParent = saveUser("current-parent", Role.PARENT, family);
        User otherParent = saveUser("other-parent", Role.PARENT, otherFamily);

        seniorRepository.saveAndFlush(senior("first-senior", otherFamily, child, otherParent, 37.1, 127.1));
        seniorRepository.saveAndFlush(senior("linked-senior", family, child, currentParent, 37.2, 127.2));

        var response = deviceService().getHomeLocation(currentParent);

        assertThat(response.latitude()).isEqualTo(37.2);
        assertThat(response.longitude()).isEqualTo(127.2);
    }

    private Senior senior(String name, Family family, User registeredBy, User parent,
                          double latitude, double longitude) {
        return Senior.builder()
                .name(name)
                .birth(LocalDate.of(1950, 1, 1))
                .phoneNumber("01012345678")
                .family(family)
                .registeredBy(registeredBy)
                .parentUser(parent)
                .latitude(latitude)
                .longitude(longitude)
                .build();
    }

    private User saveUser(String prefix, Role role) {
        return saveUser(prefix, role, null);
    }

    private User saveUser(String prefix, Role role, Family family) {
        String unique = UUID.randomUUID().toString();

        User user = userRepository.saveAndFlush(
                User.builder()
                        .loginId(prefix + "-" + unique)
                        .email(prefix + "-" + unique + "@test.com")
                        .password("encoded-password")
                        .name(prefix)
                        .role(role)
                        .family(family)
                        .build()
        );

        if (family != null) {
            familyMemberRepository.saveAndFlush(
                    FamilyMember.builder()
                            .user(user)
                            .family(family)
                            .managerType(role == Role.CHILD ? ManagerType.PRIMARY : ManagerType.NONE)
                            .build()
            );
            user.updateFamily(family);
        }

        return user;
    }
}
