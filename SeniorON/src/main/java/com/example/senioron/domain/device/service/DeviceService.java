package com.example.senioron.domain.device.service;

import com.example.senioron.domain.device.entity.Device;
import com.example.senioron.domain.device.entity.DeviceStatus;
import com.example.senioron.domain.device.repository.DeviceRepository;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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

        Optional<Device> existingDevice = deviceRepository.findByDeviceIdentifier(deviceIdentifier);
        if (existingDevice.isPresent()) {
            Device device = existingDevice.get();
            applyToken(device, user, deviceToken);
            deviceRepository.save(device);
            return;
        }

        Device newDevice = Device.builder()
                .user(user)
                .deviceIdentifier(deviceIdentifier)
                .build();
        applyToken(newDevice, user, deviceToken);

        try {
            deviceRepository.saveAndFlush(newDevice);
        } catch (DataIntegrityViolationException e) {
            Device device = deviceRepository.findByDeviceIdentifier(deviceIdentifier)
                    .orElseThrow(() -> e);
            applyToken(device, user, deviceToken);
            deviceRepository.save(device);
        }
    }

    private void applyToken(
            Device device,
            User user,
            String deviceToken
    ) {
        device.reassignOwner(user);
        device.updateDeviceToken(deviceToken);

        if (device.getConnectionStatus()
                == DeviceStatus.DISCONNECTED) {
            return;
        }

        device.updateDeviceStatus(
                DeviceStatus.ONLINE,
                device.getBatteryLevel(),
                LocalDateTime.now()
        );
    }

    @Transactional
    public void clearToken(User user, String deviceIdentifier) {
        if (deviceIdentifier == null || deviceIdentifier.isBlank()) {
            return;
        }

        deviceRepository.clearTokenIfOwnedBy(deviceIdentifier, user.getUsersId(), DeviceStatus.DISCONNECTED);
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

    @Transactional
    public void reconnectDevice(
            User seniorUser
    ) {
        if (seniorUser.getRole() != Role.PARENT) {
            throw new BusinessException(
                    ErrorCode.SENIOR_DEVICE_ACCESS_DENIED
            );
        }

        List<Device> devices =
                deviceRepository.findAllByUser(
                        seniorUser
                );

        if (devices.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.DEVICE_NOT_FOUND
            );
        }

        boolean hasDisconnectedDevice =
                devices.stream()
                        .anyMatch(device ->
                                device.getConnectionStatus()
                                        == DeviceStatus.DISCONNECTED
                        );

        if (!hasDisconnectedDevice) {
            return;
        }

        devices.stream()
                .filter(device ->
                        device.getConnectionStatus()
                                == DeviceStatus.DISCONNECTED
                )
                .forEach(Device::reconnect);
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
