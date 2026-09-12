package com.example.senioron.domain.device.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.senioron.domain.device.entity.Device;
import com.example.senioron.domain.device.entity.DeviceStatus;
import com.example.senioron.domain.device.repository.DeviceRepository;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.repository.FamilyRepository;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.senior.repository.SeniorRepository;
import com.example.senioron.domain.user.repository.UserRepository;
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

    private DeviceService deviceService;

    private DeviceService deviceService() {
        if (deviceService == null) {
            deviceService = new DeviceService(
                    deviceRepository,
                    seniorRepository
            );
        }
        return deviceService;
    }

    @Test
    void logoutClearsDeviceTokenForThatDevice() {
        User parent = saveUser("parent", Role.PARENT);

        deviceService().registerToken(parent, "fcm-token-parent", DEVICE_IDENTIFIER);
        deviceService().clearToken(parent, DEVICE_IDENTIFIER);

        Device device = deviceRepository.findByDeviceIdentifier(DEVICE_IDENTIFIER).orElseThrow();
        assertThat(device.getDeviceToken()).isNull();
        assertThat(device.getConnectionStatus()).isEqualTo(DeviceStatus.DISCONNECTED);
    }

    // 기기 A에서 부모 로그인 → 로그아웃 → 같은 기기 A에서 자녀 로그인.
    // 기기 row와 토큰 소유자는 자녀 계정으로 변경되지만,
    // 로그인만으로는 기기 연결 상태가 복구되지 않아 DISCONNECTED를 유지한다.
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
                .isEqualTo(DeviceStatus.DISCONNECTED);
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
                .familyCode("family-" + UUID.randomUUID()).build());
        User child = saveUser("child-owner", Role.CHILD, family);
        User currentParent = saveUser("current-parent", Role.PARENT, family);
        User otherParent = saveUser("other-parent", Role.PARENT, family);

        seniorRepository.saveAndFlush(senior("first-senior", family, child, otherParent, 37.1, 127.1));
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

        return userRepository.saveAndFlush(
                User.builder()
                        .loginId(prefix + "-" + unique)
                        .email(prefix + "-" + unique + "@test.com")
                        .password("encoded-password")
                        .name(prefix)
                        .role(role)
                        .family(family)
                        .build()
        );
    }
}
