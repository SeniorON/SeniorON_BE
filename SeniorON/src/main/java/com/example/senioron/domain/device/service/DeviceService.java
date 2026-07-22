package com.example.senioron.domain.device.service;

import com.example.senioron.domain.device.entity.Device;
import com.example.senioron.domain.device.entity.DeviceStatus;
import com.example.senioron.domain.device.repository.DeviceRepository;
import com.example.senioron.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;

    // 로그인 시 호출. 유저당 기기 1개를 유지하며 토큰/연결상태를 갱신한다.
    @Transactional
    public void registerToken(User user, String deviceToken) {
        Device device = deviceRepository.findFirstByUser(user).orElse(null);

        if (device == null) {
            deviceRepository.save(
                    Device.builder()
                            .user(user)
                            .deviceToken(deviceToken)
                            .connectionStatus(DeviceStatus.ONLINE)
                            .lastConnectedAt(LocalDateTime.now())
                            .build()
            );
            return;
        }

        device.updateDeviceToken(deviceToken);
        device.updateDeviceStatus(DeviceStatus.ONLINE, device.getBatteryLevel(), LocalDateTime.now());
    }
}
