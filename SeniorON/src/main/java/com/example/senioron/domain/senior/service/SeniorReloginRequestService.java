package com.example.senioron.domain.senior.service;

import com.example.senioron.domain.device.entity.Device;
import com.example.senioron.domain.device.repository.DeviceRepository;
import com.example.senioron.domain.device.service.DeviceService;
import com.example.senioron.domain.senior.dto.request.SeniorReloginRequestCreateRequest;
import com.example.senioron.domain.senior.dto.response.SeniorReloginRequestCreateResponse;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.entity.SeniorReloginRequest;
import com.example.senioron.domain.senior.entity.SeniorReloginRequestStatus;
import com.example.senioron.domain.senior.repository.SeniorReloginRequestRepository;
import com.example.senioron.domain.senior.repository.SeniorRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class SeniorReloginRequestService {

    private static final int RELOGIN_REQUEST_EXPIRATION_MINUTES = 10;

    private final DeviceService deviceService;
    private final DeviceRepository deviceRepository;
    private final SeniorRepository seniorRepository;
    private final SeniorReloginRequestRepository seniorReloginRequestRepository;

    public SeniorReloginRequestCreateResponse create(SeniorReloginRequestCreateRequest request) {
        if (!deviceService.verifyDeviceCredential(request.deviceIdentifier(), request.deviceAuthToken())) {
            throw new BusinessException(ErrorCode.INVALID_DEVICE_CREDENTIAL);
        }

        Device device = deviceRepository.findByDeviceIdentifier(request.deviceIdentifier())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_DEVICE_CREDENTIAL));

        if (device.getUser() == null) {
            throw new BusinessException(ErrorCode.INVALID_DEVICE_CREDENTIAL);
        }

        Senior senior = seniorRepository.findByParentUser(device.getUser())
                .orElseThrow(() -> new BusinessException(ErrorCode.SENIOR_RELOGIN_REQUEST_PARENT_ONLY));

        LocalDateTime now = LocalDateTime.now();
        SeniorReloginRequest reloginRequest = findReusablePendingRequest(senior, device, now);
        if (reloginRequest != null) {
            return SeniorReloginRequestCreateResponse.from(reloginRequest);
        }

        SeniorReloginRequest newRequest = SeniorReloginRequest.builder()
                .senior(senior)
                .device(device)
                .status(SeniorReloginRequestStatus.PENDING)
                .expiresAt(now.plusMinutes(RELOGIN_REQUEST_EXPIRATION_MINUTES))
                .build();

        return SeniorReloginRequestCreateResponse.from(seniorReloginRequestRepository.save(newRequest));
    }

    private SeniorReloginRequest findReusablePendingRequest(Senior senior, Device device, LocalDateTime now) {
        SeniorReloginRequest reusableRequest = null;

        for (SeniorReloginRequest request : seniorReloginRequestRepository
                .findAllBySeniorAndDeviceAndStatusOrderByCreatedAtDesc(
                        senior,
                        device,
                        SeniorReloginRequestStatus.PENDING
                )) {
            if (request.isPendingAndValid(now) && reusableRequest == null) {
                reusableRequest = request;
                continue;
            }

            if (request.isExpired(now)) {
                request.expire();
            }
        }

        return reusableRequest;
    }
}
