package com.example.senioron.domain.device.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.senioron.domain.device.entity.Device;
import com.example.senioron.domain.device.entity.DeviceStatus;
import com.example.senioron.domain.device.repository.DeviceRepository;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 같은 device_identifier로 최초 등록 요청 두 개가 동시에 들어오는 경쟁 상태에서,
 * registerToken이 유니크 제약 위반 예외를 그대로 전파하지 않고 먼저 커밋된 row를
 * 재조회해 갱신하는지 검증한다.
 */
class DeviceServiceRegisterTokenRaceTest {

    private final DeviceRepository deviceRepository = mock(DeviceRepository.class);
    private final DeviceService deviceService = new DeviceService(deviceRepository);

    @Test
    void retriesByRefetchingWhenConcurrentFirstRegistrationRacesOnUniqueConstraint() {
        User user = User.builder().usersId(1L).role(Role.CHILD).build();
        String deviceIdentifier = "device-A";

        Device concurrentlyInsertedDevice = Device.builder()
                .user(User.builder().usersId(2L).role(Role.PARENT).build())
                .deviceIdentifier(deviceIdentifier)
                .build();

        given(deviceRepository.findByDeviceIdentifier(deviceIdentifier))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(concurrentlyInsertedDevice));
        given(deviceRepository.saveAndFlush(any(Device.class)))
                .willThrow(new DataIntegrityViolationException("duplicate device_identifier"));

        deviceService.registerToken(user, "fcm-token", deviceIdentifier);

        ArgumentCaptor<Device> savedDeviceCaptor = ArgumentCaptor.forClass(Device.class);
        verify(deviceRepository).save(savedDeviceCaptor.capture());
        Device savedDevice = savedDeviceCaptor.getValue();

        assertThat(savedDevice).isSameAs(concurrentlyInsertedDevice);
        assertThat(savedDevice.getUser().getUsersId()).isEqualTo(user.getUsersId());
        assertThat(savedDevice.getDeviceToken()).isEqualTo("fcm-token");
        assertThat(savedDevice.getConnectionStatus()).isEqualTo(DeviceStatus.ONLINE);
    }
}
