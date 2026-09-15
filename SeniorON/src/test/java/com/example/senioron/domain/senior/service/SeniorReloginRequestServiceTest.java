package com.example.senioron.domain.senior.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import com.example.senioron.domain.device.entity.Device;
import com.example.senioron.domain.device.repository.DeviceRepository;
import com.example.senioron.domain.device.service.DeviceCredentialIssueResult;
import com.example.senioron.domain.device.service.DeviceService;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.repository.FamilyMemberRepository;
import com.example.senioron.domain.family.repository.FamilyRepository;
import com.example.senioron.domain.senior.dto.request.SeniorReloginRequestCreateRequest;
import com.example.senioron.domain.senior.dto.response.SeniorReloginRequestCreateResponse;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.entity.SeniorReloginRequest;
import com.example.senioron.domain.senior.entity.SeniorReloginRequestStatus;
import com.example.senioron.domain.senior.repository.SeniorReloginRequestRepository;
import com.example.senioron.domain.senior.repository.SeniorRepository;
import com.example.senioron.domain.user.entity.RefreshToken;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.RefreshTokenRepository;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@DataJpaTest
class SeniorReloginRequestServiceTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private SeniorRepository seniorRepository;

    @Autowired
    private FamilyRepository familyRepository;

    @Autowired
    private FamilyMemberRepository familyMemberRepository;

    @Autowired
    private SeniorReloginRequestRepository seniorReloginRequestRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private DeviceService deviceService;
    private SeniorReloginRequestService seniorReloginRequestService;

    @Test
    void createSucceedsForRegisteredSeniorDeviceWithValidDeviceAuthToken() {
        SeniorDeviceFixture fixture = saveSeniorDeviceFixture("device-1");

        SeniorReloginRequestCreateResponse response = service().create(createRequest(
                fixture.deviceIdentifier(),
                fixture.deviceAuthToken()
        ));

        assertThat(response.getSeniorReloginRequestId()).isNotNull();
        assertThat(response.getSeniorId()).isEqualTo(fixture.senior().getSeniorId());
        assertThat(response.getDeviceId()).isEqualTo(fixture.device().getDeviceId());
        assertThat(response.getStatus()).isEqualTo(SeniorReloginRequestStatus.PENDING);
        assertThat(response.getExpiresAt()).isAfter(LocalDateTime.now());
        assertThat(seniorReloginRequestRepository.count()).isEqualTo(1L);
    }

    @Test
    void createFailsWhenDeviceIdentifierDoesNotExist() {
        assertThatThrownBy(() -> service().create(createRequest("missing-device", "token")))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.INVALID_DEVICE_CREDENTIAL);

        assertThat(seniorReloginRequestRepository.count()).isZero();
    }

    @Test
    void createFailsWhenDeviceAuthTokenIsWrong() {
        SeniorDeviceFixture fixture = saveSeniorDeviceFixture("device-1");

        assertThatThrownBy(() -> service().create(createRequest(fixture.deviceIdentifier(), "wrong-token")))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.INVALID_DEVICE_CREDENTIAL);

        assertThat(seniorReloginRequestRepository.count()).isZero();
    }

    @Test
    void createFailsWhenAnotherDeviceAuthTokenIsUsed() {
        SeniorDeviceFixture deviceA = saveSeniorDeviceFixture("device-A");
        SeniorDeviceFixture deviceB = saveSeniorDeviceFixture("device-B");

        assertThatThrownBy(() -> service().create(createRequest(
                deviceB.deviceIdentifier(),
                deviceA.deviceAuthToken()
        )))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.INVALID_DEVICE_CREDENTIAL);

        assertThat(seniorReloginRequestRepository.count()).isZero();
    }

    @Test
    void createFailsWhenDeviceUserIsNotSeniorParentUser() {
        User parentUser = saveUser("parent-without-senior", Role.PARENT);
        DeviceCredentialIssueResult credential = deviceService().registerDevice(parentUser, "device-1");

        assertThatThrownBy(() -> service().create(createRequest("device-1", credential.deviceAuthToken())))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.SENIOR_RELOGIN_REQUEST_PARENT_ONLY);

        assertThat(seniorReloginRequestRepository.count()).isZero();
    }

    @Test
    void createReusesValidPendingRequestForSameSeniorAndDevice() {
        SeniorDeviceFixture fixture = saveSeniorDeviceFixture("device-1");

        SeniorReloginRequestCreateResponse first = service().create(createRequest(
                fixture.deviceIdentifier(),
                fixture.deviceAuthToken()
        ));
        SeniorReloginRequestCreateResponse second = service().create(createRequest(
                fixture.deviceIdentifier(),
                fixture.deviceAuthToken()
        ));

        assertThat(second.getSeniorReloginRequestId()).isEqualTo(first.getSeniorReloginRequestId());
        assertThat(seniorReloginRequestRepository.count()).isEqualTo(1L);
    }

    @Test
    void createCreatesNewRequestWhenExistingPendingRequestIsExpired() {
        SeniorDeviceFixture fixture = saveSeniorDeviceFixture("device-1");
        SeniorReloginRequest expiredRequest = seniorReloginRequestRepository.saveAndFlush(SeniorReloginRequest.builder()
                .senior(fixture.senior())
                .device(fixture.device())
                .status(SeniorReloginRequestStatus.PENDING)
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build());

        SeniorReloginRequestCreateResponse response = service().create(createRequest(
                fixture.deviceIdentifier(),
                fixture.deviceAuthToken()
        ));

        assertThat(response.getSeniorReloginRequestId()).isNotEqualTo(expiredRequest.getSeniorReloginRequestId());
        assertThat(seniorReloginRequestRepository.count()).isEqualTo(2L);
        assertThat(seniorReloginRequestRepository.findById(expiredRequest.getSeniorReloginRequestId()))
                .hasValueSatisfying(request ->
                        assertThat(request.getStatus()).isEqualTo(SeniorReloginRequestStatus.EXPIRED)
                );
    }

    @Test
    void createSucceedsAfterRefreshTokenWasDeleted() {
        SeniorDeviceFixture fixture = saveSeniorDeviceFixture("device-1");
        refreshTokenRepository.saveAndFlush(RefreshToken.builder()
                .user(fixture.device().getUser())
                .deviceIdentifier(fixture.deviceIdentifier())
                .tokenHash("refresh-token-hash-" + UUID.randomUUID())
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build());
        refreshTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());

        SeniorReloginRequestCreateResponse response = service().create(createRequest(
                fixture.deviceIdentifier(),
                fixture.deviceAuthToken()
        ));

        assertThat(refreshTokenRepository.count()).isZero();
        assertThat(response.getStatus()).isEqualTo(SeniorReloginRequestStatus.PENDING);
        assertThat(seniorReloginRequestRepository.count()).isEqualTo(1L);
    }

    private SeniorReloginRequestService service() {
        if (seniorReloginRequestService == null) {
            seniorReloginRequestService = new SeniorReloginRequestService(
                    deviceService(),
                    deviceRepository,
                    seniorRepository,
                    seniorReloginRequestRepository
            );
        }
        return seniorReloginRequestService;
    }

    private DeviceService deviceService() {
        if (deviceService == null) {
            deviceService = new DeviceService(
                    deviceRepository,
                    seniorRepository,
                    familyMemberRepository,
                    passwordEncoder
            );
        }
        return deviceService;
    }

    private SeniorReloginRequestCreateRequest createRequest(String deviceIdentifier, String deviceAuthToken) {
        return new SeniorReloginRequestCreateRequest(deviceIdentifier, deviceAuthToken);
    }

    private SeniorDeviceFixture saveSeniorDeviceFixture(String deviceIdentifier) {
        Family family = familyRepository.saveAndFlush(Family.builder()
                .seniorCode("family-" + UUID.randomUUID())
                .build());
        User child = saveUser("child", Role.CHILD);
        User parent = saveUser("parent", Role.PARENT);
        Senior senior = seniorRepository.saveAndFlush(Senior.builder()
                .name("시니어")
                .birth(LocalDate.of(1950, 1, 1))
                .phoneNumber("01012345678")
                .family(family)
                .registeredBy(child)
                .parentUser(parent)
                .build());
        DeviceCredentialIssueResult credential = deviceService().registerDevice(parent, deviceIdentifier);
        Device device = deviceRepository.findByDeviceIdentifier(deviceIdentifier).orElseThrow();
        return new SeniorDeviceFixture(senior, device, deviceIdentifier, credential.deviceAuthToken());
    }

    private User saveUser(String prefix, Role role) {
        String unique = UUID.randomUUID().toString();
        return userRepository.saveAndFlush(User.builder()
                .loginId(prefix + "-" + unique)
                .email(prefix + "-" + unique + "@test.com")
                .password("encoded-password")
                .name(prefix)
                .role(role)
                .build());
    }

    private record SeniorDeviceFixture(
            Senior senior,
            Device device,
            String deviceIdentifier,
            String deviceAuthToken
    ) {
    }
}
