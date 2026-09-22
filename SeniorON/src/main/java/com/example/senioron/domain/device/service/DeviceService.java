package com.example.senioron.domain.device.service;

import com.example.senioron.domain.device.entity.Device;
import com.example.senioron.domain.device.entity.DeviceStatus;
import com.example.senioron.domain.device.repository.DeviceRepository;
import com.example.senioron.domain.family.entity.FamilyMember;
import com.example.senioron.domain.family.repository.FamilyMemberRepository;
import com.example.senioron.domain.senior.repository.SeniorRepository;
import com.example.senioron.domain.device.dto.request.DeviceLocationUpdateRequest;
import com.example.senioron.domain.device.dto.response.DeviceLocationResponse;
import com.example.senioron.domain.device.dto.response.HomeLocationResponse;
import com.example.senioron.domain.senior.entity.Senior;
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
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private static final int DEVICE_AUTH_TOKEN_BYTE_LENGTH = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final DeviceRepository deviceRepository;

    private final SeniorRepository seniorRepository;

    private final FamilyMemberRepository familyMemberRepository;

    private final PasswordEncoder passwordEncoder;

    @Transactional
    public DeviceCredentialIssueResult registerDevice(User user, String deviceIdentifier) {
        if (deviceIdentifier == null || deviceIdentifier.isBlank()) {
            return DeviceCredentialIssueResult.empty();
        }

        Device device = findOrCreateDevice(user, deviceIdentifier);
        return issueDeviceCredentialIfMissing(device);
    }

    @Transactional
    public void updateFcmToken(User user, String deviceToken, String deviceIdentifier) {
        if (deviceIdentifier == null || deviceIdentifier.isBlank()) {
            throw new BusinessException(ErrorCode.DEVICE_IDENTIFIER_REQUIRED);
        }

        Device device = findOrCreateDevice(user, deviceIdentifier);
        applyFcmToken(device, user, deviceToken);
        deviceRepository.save(device);
    }

    @Transactional
    public void registerToken(User user, String deviceToken, String deviceIdentifier) {
        updateFcmToken(user, deviceToken, deviceIdentifier);
    }

    @Transactional(readOnly = true)
    public boolean verifyDeviceCredential(String deviceIdentifier, String deviceAuthToken) {
        if (deviceIdentifier == null || deviceIdentifier.isBlank()
                || deviceAuthToken == null || deviceAuthToken.isBlank()) {
            return false;
        }

        return deviceRepository.findByDeviceIdentifier(deviceIdentifier)
                .filter(Device::hasDeviceAuthToken)
                .filter(device -> passwordEncoder.matches(deviceAuthToken, device.getDeviceAuthTokenHash()))
                .isPresent();
    }

    private Device findOrCreateDevice(User user, String deviceIdentifier) {
        Optional<Device> existingDevice = deviceRepository.findByDeviceIdentifier(deviceIdentifier);
        if (existingDevice.isPresent()) {
            Device device = existingDevice.get();
            device.reassignOwner(user);
            return device;
        }

        Device newDevice = Device.builder()
                .user(user)
                .deviceIdentifier(deviceIdentifier)
                .build();

        try {
            return deviceRepository.saveAndFlush(newDevice);
        } catch (DataIntegrityViolationException e) {
            Device device = deviceRepository.findByDeviceIdentifier(deviceIdentifier)
                    .orElseThrow(() -> e);
            device.reassignOwner(user);
            return device;
        }
    }

    private void applyFcmToken(
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

    private DeviceCredentialIssueResult issueDeviceCredentialIfMissing(Device device) {
        if (device.hasDeviceAuthToken()) {
            return DeviceCredentialIssueResult.empty();
        }

        String deviceAuthToken = generateDeviceAuthToken();
        device.issueDeviceAuthTokenHash(passwordEncoder.encode(deviceAuthToken));
        return DeviceCredentialIssueResult.issued(deviceAuthToken);
    }

    private String generateDeviceAuthToken() {
        byte[] randomBytes = new byte[DEVICE_AUTH_TOKEN_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    @Transactional
    public void clearToken(User user, String deviceIdentifier) {
        if (deviceIdentifier == null || deviceIdentifier.isBlank()) {
            return;
        }

        deviceRepository.clearTokenIfOwnedBy(
                deviceIdentifier,
                user.getUsersId()
        );
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
                request.charging(),
                request.deviceStatusSharingEnabled(),
                request.networkConnected(),
                request.defaultHomeEnabled(),
                request.locationPermissionGranted(),
                request.gpsEnabled(),
                request.notificationPermissionGranted(),
                request.appExecutionMaintained(),
                LocalDateTime.now()
        );

        deviceRepository.save(device);
    }

    @Transactional
    public void disconnectDevice(
            User currentUser,
            Long seniorId
    ) {

        if (currentUser.getRole() != Role.CHILD) {
            throw new BusinessException(
                    ErrorCode.DEVICE_DISCONNECT_ACCESS_DENIED
            );
        }

        Senior senior =
                seniorRepository
                        .findById(seniorId)
                        .orElseThrow(() ->
                                new BusinessException(
                                        ErrorCode.SENIOR_NOT_FOUND
                                )
                        );

        FamilyMember familyMember =
                familyMemberRepository
                        .findByUserAndFamily(
                                currentUser,
                                senior.getFamily()
                        )
                        .orElseThrow(() ->
                                new BusinessException(
                                        ErrorCode.DEVICE_DISCONNECT_ACCESS_DENIED
                                )
                        );

        validateDeviceDisconnectAuthority(
                currentUser,
                familyMember
        );

        User seniorUser =
                senior.getParentUser();

        if (seniorUser == null) {
            throw new BusinessException(
                    ErrorCode.DEVICE_NOT_CONNECTED
            );
        }

        List<Device> devices =
                deviceRepository.findAllByUser(
                        seniorUser
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
            return;
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
            User currentUser,
            FamilyMember familyMember
    ) {

        if (currentUser.getRole() != Role.CHILD) {
            throw new BusinessException(
                    ErrorCode.DEVICE_DISCONNECT_ACCESS_DENIED
            );
        }

        ManagerType managerType =
                familyMember.getManagerType();

        if (managerType != ManagerType.PRIMARY
                && managerType != ManagerType.SUB) {
            throw new BusinessException(
                    ErrorCode.DEVICE_DISCONNECT_ACCESS_DENIED
            );
        }
    }

    @Transactional
    public void updateLocation(
            User currentUser,
            DeviceLocationUpdateRequest request
    ) {
        if (currentUser.getRole() != Role.PARENT) {
            throw new BusinessException(
                    ErrorCode.SENIOR_DEVICE_ACCESS_DENIED
            );
        }

        Device device = deviceRepository
                .findByDeviceIdentifierAndUser(
                        request.deviceIdentifier(),
                        currentUser
                )
                .orElseThrow(() ->
                        new BusinessException(
                                ErrorCode.DEVICE_NOT_FOUND
                        )
                );

        if (device.getConnectionStatus()
                == DeviceStatus.DISCONNECTED) {
            throw new BusinessException(
                    ErrorCode.DEVICE_NOT_CONNECTED
            );
        }

        device.updateLocation(
                request.latitude(),
                request.longitude(),
                LocalDateTime.now()
        );
    }

    @Transactional(readOnly = true)
    public DeviceLocationResponse getLatestLocation(
            User currentUser,
            Long seniorId
    ) {

        if (currentUser.getRole() != Role.CHILD) {
            throw new BusinessException(
                    ErrorCode.SENIOR_DEVICE_ACCESS_DENIED
            );
        }

        Senior senior =
                seniorRepository
                        .findById(seniorId)
                        .orElseThrow(() ->
                                new BusinessException(
                                        ErrorCode.SENIOR_NOT_FOUND
                                )
                        );

        familyMemberRepository
                .findByUserAndFamily(
                        currentUser,
                        senior.getFamily()
                )
                .orElseThrow(() ->
                        new BusinessException(
                                ErrorCode.SENIOR_DEVICE_ACCESS_DENIED
                        )
                );

        User seniorUser =
                senior.getParentUser();

        if (seniorUser == null) {
            throw new BusinessException(
                    ErrorCode.DEVICE_LOCATION_NOT_FOUND
            );
        }

        Device device =
                deviceRepository
                        .findFirstByUserAndLastLocationUpdatedAtIsNotNullOrderByLastLocationUpdatedAtDescDeviceIdDesc(
                                seniorUser
                        )
                        .orElseThrow(() ->
                                new BusinessException(
                                        ErrorCode.DEVICE_LOCATION_NOT_FOUND
                                )
                        );

        return DeviceLocationResponse.from(device);
    }

    @Transactional(readOnly = true)
    public HomeLocationResponse getHomeLocation(
            User currentUser
    ) {
        if (currentUser.getRole() != Role.PARENT) {
            throw new BusinessException(
                    ErrorCode.SENIOR_DEVICE_ACCESS_DENIED
            );
        }

        Senior senior = seniorRepository
                .findByParentUser(currentUser)
                .orElseThrow(() ->
                        new BusinessException(
                                ErrorCode.SENIOR_NOT_FOUND
                        )
                );

        if (senior.getLatitude() == null
                || senior.getLongitude() == null) {
            throw new BusinessException(
                    ErrorCode.SENIOR_HOME_LOCATION_NOT_FOUND
            );
        }

        return HomeLocationResponse.from(senior);
    }

}
