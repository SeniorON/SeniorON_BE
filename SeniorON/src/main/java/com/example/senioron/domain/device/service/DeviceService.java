package com.example.senioron.domain.device.service;

import com.example.senioron.domain.device.entity.Device;
import com.example.senioron.domain.device.entity.DeviceStatus;
import com.example.senioron.domain.device.repository.DeviceRepository;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.senioron.domain.device.dto.request.DeviceStatusUpdateRequest;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.ManagerType;
import java.util.List;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;
    @Transactional
    public void registerToken(User user, String deviceToken, String deviceIdentifier) {
        if (deviceIdentifier == null || deviceIdentifier.isBlank()) {
            throw new BusinessException(ErrorCode.DEVICE_IDENTIFIER_REQUIRED);
        }

        Device device = deviceRepository.findByDeviceIdentifier(deviceIdentifier)
                .orElseGet(() -> Device.builder()
                        .user(user)
                        .deviceIdentifier(deviceIdentifier)
                        .build());

        // 같은 기기에서 다른 계정으로 로그인한 경우 소유자를 새 계정으로 교체한다.
        device.reassignOwner(user);
        device.updateDeviceToken(deviceToken);
        device.updateDeviceStatus(
                DeviceStatus.ONLINE,
                device.getBatteryLevel(),
                LocalDateTime.now()
        );

        deviceRepository.save(device);
    }

    // 로그아웃 시 해당 기기의 FCM 토큰을 비활성화한다. 이미 다른 계정으로 소유자가 넘어간
    // 기기라면(재로그인 등으로 로그아웃보다 먼저 소유자가 바뀐 경우) 건드리지 않는다.
    @Transactional
    public void clearToken(User user, String deviceIdentifier) {
        if (deviceIdentifier == null || deviceIdentifier.isBlank()) {
            return;
        }

        deviceRepository.findByDeviceIdentifier(deviceIdentifier)
                .filter(device -> device.getUser() != null
                        && device.getUser().getUsersId().equals(user.getUsersId()))
                .ifPresent(device -> {
                    device.updateDeviceToken(null);
                    device.disconnect();
                });
    }

    @Transactional
    public void updateDeviceStatus(
            User user,
            DeviceStatusUpdateRequest request
    ) {
        if (user.getRole() != Role.PARENT) {
            throw new BusinessException(
                    ErrorCode.SENIOR_DEVICE_ACCESS_DENIED
            );
        }

        if (user.getFamily() == null) {
            throw new BusinessException(
                    ErrorCode.FAMILY_NOT_CONNECTED
            );
        }

        Optional<Device> latestDevice =
                deviceRepository
                        .findFirstByUserOrderByLastConnectedAtDescDeviceIdDesc(
                                user
                        );

        if (latestDevice.isPresent()
                && latestDevice.get().getConnectionStatus()
                == DeviceStatus.DISCONNECTED) {

            throw new BusinessException(
                    ErrorCode.DEVICE_NOT_CONNECTED
            );
        }

        Device device = deviceRepository
                .findByDeviceIdentifier(
                        request.deviceIdentifier()
                )
                .orElseGet(() ->
                        Device.builder()
                                .user(user)
                                .deviceIdentifier(
                                        request.deviceIdentifier()
                                )
                                .build()
                );

        device.reassignOwner(user);
        device.updateDeviceInfo(
                request.deviceName(),
                DeviceStatus.ONLINE,
                request.batteryLevel(),
                LocalDateTime.now()
        );

        deviceRepository.save(device);
    }

    @Transactional
    public void disconnectDevice(
            User currentUser
    ) {
        validateDeviceDisconnectAuthority(currentUser);

        if (currentUser.getFamily() == null) {
            throw new BusinessException(
                    ErrorCode.FAMILY_NOT_CONNECTED
            );
        }

        List<Device> devices =
                deviceRepository.findAllByUser_FamilyAndUser_Role(
                        currentUser.getFamily(),
                        Role.PARENT
                );

        if (devices.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.DEVICE_NOT_CONNECTED
            );
        }

        boolean alreadyDisconnected =
                devices.stream()
                        .allMatch(device ->
                                device.getConnectionStatus()
                                        == DeviceStatus.DISCONNECTED
                        );

        if (alreadyDisconnected) {
            throw new BusinessException(
                    ErrorCode.DEVICE_NOT_CONNECTED
            );
        }

        devices.forEach(Device::disconnect);
    }

    private void validateDeviceDisconnectAuthority(
            User currentUser
    ) {
        if (currentUser.getRole() != Role.CHILD) {
            throw new BusinessException(
                    ErrorCode.DEVICE_DISCONNECT_ACCESS_DENIED
            );
        }

        ManagerType managerType =
                currentUser.getManagerType();

        if (managerType != ManagerType.PRIMARY
                && managerType != ManagerType.SUB) {
            throw new BusinessException(
                    ErrorCode.DEVICE_DISCONNECT_ACCESS_DENIED
            );
        }
    }
}
