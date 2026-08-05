package com.example.senioron.domain.device.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.senioron.domain.device.entity.Device;
import com.example.senioron.domain.device.entity.DeviceStatus;
import com.example.senioron.domain.device.repository.DeviceRepository;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import java.util.UUID;
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

    private DeviceService deviceService;

    private DeviceService deviceService() {
        if (deviceService == null) {
            deviceService = new DeviceService(deviceRepository);
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

    // 기기 A에서 부모 로그인 → 토큰 등록 → 부모 로그아웃 → 같은 기기 A에서 자녀 로그인 → 토큰 등록.
    // 이 시점에 device-A와 연결된 row가 자녀 것 1개만 남아있어야 한다.
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
        assertThat(device.getConnectionStatus()).isEqualTo(DeviceStatus.ONLINE);
    }

    private User saveUser(String prefix, Role role) {
        String unique = UUID.randomUUID().toString();

        return userRepository.saveAndFlush(
                User.builder()
                        .loginId(prefix + "-" + unique)
                        .email(prefix + "-" + unique + "@test.com")
                        .password("encoded-password")
                        .name(prefix)
                        .role(role)
                        .build()
        );
    }
}
