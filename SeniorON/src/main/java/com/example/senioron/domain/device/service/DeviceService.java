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
    @Transactional
    public void registerToken(User user, String deviceToken, String deviceIdentifier) {
        Device device = deviceRepository.findByUserAndDeviceIdentifier(user, deviceIdentifier)
                .orElseGet(() -> Device.builder()
                        .user(user)
                        .deviceIdentifier(deviceIdentifier)
                        .build());

        device.updateDeviceToken(deviceToken);
        device.updateDeviceStatus(DeviceStatus.ONLINE, device.getBatteryLevel(), LocalDateTime.now());

        deviceRepository.save(device);
    }
}
