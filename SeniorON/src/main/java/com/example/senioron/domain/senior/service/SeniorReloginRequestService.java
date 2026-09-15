package com.example.senioron.domain.senior.service;

import com.example.senioron.domain.device.entity.Device;
import com.example.senioron.domain.device.repository.DeviceRepository;
import com.example.senioron.domain.device.service.DeviceService;
import com.example.senioron.domain.family.repository.FamilyMemberRepository;
import com.example.senioron.domain.senior.dto.request.SeniorReloginRequestCreateRequest;
import com.example.senioron.domain.senior.dto.response.SeniorReloginRequestApproveResponse;
import com.example.senioron.domain.senior.dto.response.SeniorReloginRequestCreateResponse;
import com.example.senioron.domain.senior.dto.response.SeniorReloginRequestListResponse;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.entity.SeniorReloginRequest;
import com.example.senioron.domain.senior.entity.SeniorReloginRequestStatus;
import com.example.senioron.domain.senior.repository.SeniorReloginRequestRepository;
import com.example.senioron.domain.senior.repository.SeniorRepository;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class SeniorReloginRequestService {

    private static final int RELOGIN_REQUEST_EXPIRATION_MINUTES = 10;
    private static final int APPROVED_REQUEST_EXPIRATION_MINUTES = 10;

    private final DeviceService deviceService;
    private final DeviceRepository deviceRepository;
    private final SeniorRepository seniorRepository;
    private final SeniorReloginRequestRepository seniorReloginRequestRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final UserRepository userRepository;

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

    public SeniorReloginRequestApproveResponse approve(Long requestId, User principal) {
        User child = getAuthenticatedUser(principal);
        SeniorReloginRequest request = seniorReloginRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SENIOR_RELOGIN_REQUEST_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        validateGuardian(child, request.getSenior());
        validateApprovable(request, now);
        request.approve(now.plusMinutes(APPROVED_REQUEST_EXPIRATION_MINUTES));

        return SeniorReloginRequestApproveResponse.from(request);
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

    private User getAuthenticatedUser(User principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.USER_NOT_AUTHENTICATED);
        }

        return userRepository.findById(principal.getUsersId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private void validateGuardian(User child, Senior senior) {
        if (child.getRole() != Role.CHILD
                || !familyMemberRepository.existsByUserAndFamily(child, senior.getFamily())) {
            throw new BusinessException(ErrorCode.SENIOR_RELOGIN_REQUEST_ACCESS_DENIED);
        }
    }

    private void validateApprovable(SeniorReloginRequest request, LocalDateTime now) {
        if (request.getStatus() != SeniorReloginRequestStatus.PENDING) {
            throw new BusinessException(ErrorCode.SENIOR_RELOGIN_REQUEST_NOT_PENDING);
        }

        if (request.isExpired(now)) {
            request.expire();
            throw new BusinessException(ErrorCode.SENIOR_RELOGIN_REQUEST_EXPIRED);
        }

        if (!isSeniorDevice(request)) {
            throw new BusinessException(ErrorCode.SENIOR_RELOGIN_REQUEST_DEVICE_MISMATCH);
        }
    }

    private boolean isSeniorDevice(SeniorReloginRequest request) {
        User parentUser = request.getSenior().getParentUser();
        User deviceUser = request.getDevice().getUser();
        return parentUser != null
                && deviceUser != null
                && Objects.equals(parentUser.getUsersId(), deviceUser.getUsersId());
    }

    @Transactional(readOnly = true)
    public List<SeniorReloginRequestListResponse> getPendingRequests(User principal) {
        User child = getAuthenticatedUser(principal);

        if (child.getRole() != Role.CHILD) {
            throw new BusinessException(ErrorCode.SENIOR_RELOGIN_REQUEST_ACCESS_DENIED);
        }

        LocalDateTime now = LocalDateTime.now();

        return familyMemberRepository.findAllByUser(child).stream()
                .flatMap(familyMember ->
                        seniorReloginRequestRepository
                                .findAllBySeniorFamilyAndStatusOrderByCreatedAtDesc(
                                        familyMember.getFamily(),
                                        SeniorReloginRequestStatus.PENDING
                                )
                                .stream()
                )
                .filter(request -> !request.isExpired(now))
                .map(SeniorReloginRequestListResponse::from)
                .toList();
    }
}
