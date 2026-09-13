package com.example.senioron.domain.home.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.example.senioron.domain.device.repository.DeviceRepository;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.repository.FamilyRepository;
import com.example.senioron.domain.device.entity.Device;
import com.example.senioron.domain.device.entity.DeviceStatus;

import java.time.LocalDateTime;
import com.example.senioron.domain.home.dto.request.SeniorProfileUpdateRequest;
import com.example.senioron.domain.home.dto.response.HomeResponse;
import com.example.senioron.domain.home.dto.response.SeniorProfileUpdateResponse;
import com.example.senioron.domain.home.repository.ButtonOptionRepository;
import com.example.senioron.domain.home.repository.HomeRepository;
import com.example.senioron.domain.home.repository.HomeSettingRepository;
import com.example.senioron.domain.hospital.repository.HospitalRepository;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.entity.SeniorRelation;
import com.example.senioron.domain.senior.entity.UserSenior;
import com.example.senioron.domain.senior.repository.SeniorRepository;
import com.example.senioron.domain.senior.repository.UserSeniorRepository;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.entity.RefreshToken;
import com.example.senioron.domain.user.repository.RefreshTokenRepository;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.domain.home.dto.request.HomeFontSizeUpdateRequest;
import com.example.senioron.domain.home.entity.FontSize;
import com.example.senioron.domain.home.entity.HomeSetting;
import com.example.senioron.domain.home.dto.request.HomeButtonSaveRequest;
import com.example.senioron.domain.home.entity.ActionType;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.dao.DataIntegrityViolationException;

class HomeServiceSeniorProfileTest {

    private final HomeRepository homeRepository = org.mockito.Mockito.mock(HomeRepository.class);
    private final ButtonOptionRepository buttonOptionRepository = org.mockito.Mockito.mock(ButtonOptionRepository.class);
    private final UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
    private final HospitalRepository hospitalRepository = org.mockito.Mockito.mock(HospitalRepository.class);
    private final DeviceRepository deviceRepository = org.mockito.Mockito.mock(DeviceRepository.class);
    private final SeniorRepository seniorRepository = org.mockito.Mockito.mock(SeniorRepository.class);
    private final UserSeniorRepository userSeniorRepository = org.mockito.Mockito.mock(UserSeniorRepository.class);
    private final HomeSettingRepository homeSettingRepository = org.mockito.Mockito.mock(HomeSettingRepository.class);
    private final FamilyRepository familyRepository = org.mockito.Mockito.mock(FamilyRepository.class);

    private final RefreshTokenRepository refreshTokenRepository =
            org.mockito.Mockito.mock(RefreshTokenRepository.class);
    private HomeService homeService;
    private final HomeWebSocketService homeWebSocketService =
            org.mockito.Mockito.mock(HomeWebSocketService.class);

    @BeforeEach
    void setUp() {
        homeService = new HomeService(
                homeRepository,
                buttonOptionRepository,
                userRepository,
                hospitalRepository,
                deviceRepository,
                seniorRepository,
                userSeniorRepository,
                homeSettingRepository,
                familyRepository,
                refreshTokenRepository,
                homeWebSocketService
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getDeviceDetailReturnsLoginExpiredWhenRefreshTokenIsExpired() {
        Family family = Family.builder()
                .familyId(1L)
                .build();

        User child = createChild(
                1L,
                family,
                ManagerType.PRIMARY
        );

        User parent = User.builder()
                .usersId(3L)
                .name("시니어")
                .role(Role.PARENT)
                .family(family)
                .build();

        Senior senior = Senior.builder()
                .seniorId(10L)
                .name("김영희")
                .family(family)
                .registeredBy(child)
                .parentUser(parent)
                .build();

        UserSenior relation = createUserSenior(
                child,
                senior,
                SeniorRelation.MOTHER,
                null
        );

        Device device = Device.builder()
                .user(parent)
                .deviceIdentifier("device-1")
                .deviceName("Galaxy S24")
                .connectionStatus(DeviceStatus.ONLINE)
                .batteryLevel(72)
                .lastConnectedAt(LocalDateTime.now())
                .build();

        setCurrentUser(child);

        given(userSeniorRepository
                .findByUserAndSenior_SeniorId(
                        child,
                        10L
                ))
                .willReturn(Optional.of(relation));

        given(deviceRepository
                .findFirstByUserOrderByLastConnectedAtDescDeviceIdDesc(
                        parent
                ))
                .willReturn(Optional.of(device));

        RefreshToken refreshToken =
                org.mockito.Mockito.mock(RefreshToken.class);

        given(refreshTokenRepository
                .findByUserAndDeviceIdentifier(
                        parent,
                        "device-1"
                ))
                .willReturn(Optional.of(refreshToken));

        given(refreshToken.isExpired(
                any(LocalDateTime.class)
        ))
                .willReturn(true);

        var response =
                homeService.getDeviceDetail(10L);

        assertThat(response.connectionStatus())
                .isEqualTo(DeviceStatus.LOGIN_EXPIRED);

        assertThat(response.connected())
                .isFalse();
    }

    @Test
    void getDeviceDetailReturnsOnlineWithDeviceStatusFields() {
        Family family = Family.builder()
                .familyId(1L)
                .build();

        User child = createChild(
                1L,
                family,
                ManagerType.PRIMARY
        );

        User parent = User.builder()
                .usersId(3L)
                .name("시니어")
                .role(Role.PARENT)
                .family(family)
                .build();

        Senior senior = Senior.builder()
                .seniorId(10L)
                .name("김영희")
                .family(family)
                .registeredBy(child)
                .parentUser(parent)
                .build();

        UserSenior relation = createUserSenior(
                child,
                senior,
                SeniorRelation.MOTHER,
                null
        );

        Device device = Device.builder()
                .user(parent)
                .deviceIdentifier("device-1")
                .deviceName("Galaxy S24")
                .connectionStatus(DeviceStatus.ONLINE)
                .batteryLevel(72)
                .charging(true)
                .deviceStatusSharingEnabled(true)
                .networkConnected(true)
                .defaultHomeEnabled(true)
                .locationPermissionGranted(true)
                .gpsEnabled(false)
                .notificationPermissionGranted(true)
                .appExecutionMaintained(true)
                .lastConnectedAt(LocalDateTime.now())
                .build();

        setCurrentUser(child);

        given(userSeniorRepository
                .findByUserAndSenior_SeniorId(
                        child,
                        10L
                ))
                .willReturn(Optional.of(relation));

        given(deviceRepository
                .findFirstByUserOrderByLastConnectedAtDescDeviceIdDesc(
                        parent
                ))
                .willReturn(Optional.of(device));

        RefreshToken refreshToken =
                org.mockito.Mockito.mock(RefreshToken.class);

        given(refreshTokenRepository
                .findByUserAndDeviceIdentifier(
                        parent,
                        "device-1"
                ))
                .willReturn(Optional.of(refreshToken));

        given(refreshToken.isExpired(
                any(LocalDateTime.class)
        ))
                .willReturn(false);

        var response =
                homeService.getDeviceDetail(10L);

        assertThat(response.connected())
                .isTrue();

        assertThat(response.connectionStatus())
                .isEqualTo(DeviceStatus.ONLINE);

        assertThat(response.batteryLevel())
                .isEqualTo(72);

        assertThat(response.charging())
                .isTrue();

        assertThat(response.deviceStatusSharingEnabled())
                .isTrue();

        assertThat(response.networkConnected())
                .isTrue();

        assertThat(response.defaultHomeEnabled())
                .isTrue();

        assertThat(response.locationPermissionGranted())
                .isTrue();

        assertThat(response.gpsEnabled())
                .isFalse();

        assertThat(response.notificationPermissionGranted())
                .isTrue();

        assertThat(response.appExecutionMaintained())
                .isTrue();
    }

    @Test
    void getDeviceDetailReturnsOfflineWhenLastConnectedAtIsOlderThan11Minutes() {
        Family family = Family.builder()
                .familyId(1L)
                .build();

        User child = createChild(
                1L,
                family,
                ManagerType.PRIMARY
        );

        User parent = User.builder()
                .usersId(3L)
                .name("시니어")
                .role(Role.PARENT)
                .family(family)
                .build();

        Senior senior = Senior.builder()
                .seniorId(10L)
                .name("김영희")
                .family(family)
                .registeredBy(child)
                .parentUser(parent)
                .build();

        UserSenior relation = createUserSenior(
                child,
                senior,
                SeniorRelation.MOTHER,
                null
        );

        Device device = Device.builder()
                .user(parent)
                .deviceIdentifier("device-1")
                .deviceName("Galaxy S24")
                .connectionStatus(DeviceStatus.ONLINE)
                .batteryLevel(72)
                .lastConnectedAt(LocalDateTime.now().minusMinutes(12))
                .build();

        setCurrentUser(child);

        given(userSeniorRepository
                .findByUserAndSenior_SeniorId(
                        child,
                        10L
                ))
                .willReturn(Optional.of(relation));

        given(deviceRepository
                .findFirstByUserOrderByLastConnectedAtDescDeviceIdDesc(
                        parent
                ))
                .willReturn(Optional.of(device));

        RefreshToken refreshToken =
                org.mockito.Mockito.mock(RefreshToken.class);

        given(refreshTokenRepository
                .findByUserAndDeviceIdentifier(
                        parent,
                        "device-1"
                ))
                .willReturn(Optional.of(refreshToken));

        given(refreshToken.isExpired(
                any(LocalDateTime.class)
        ))
                .willReturn(false);

        var response =
                homeService.getDeviceDetail(10L);

        assertThat(response.connected())
                .isFalse();

        assertThat(response.connectionStatus())
                .isEqualTo(DeviceStatus.OFFLINE);
    }

    @Test
    void getHomeReturnsCurrentUsersSeniorRelation() {
        Family family = Family.builder()
                .familyId(1L)
                .build();

        User subChild = createChild(
                2L,
                family,
                ManagerType.SUB
        );

        User primaryChild = createChild(
                1L,
                family,
                ManagerType.PRIMARY
        );

        User parent = User.builder()
                .usersId(3L)
                .name("시니어")
                .role(Role.PARENT)
                .family(family)
                .build();

        Senior senior = createSenior(
                10L,
                family,
                primaryChild,
                parent
        );

        UserSenior subRelation = createUserSenior(
                subChild,
                senior,
                SeniorRelation.GRANDPARENT,
                null
        );

        setCurrentUser(subChild);

        given(userSeniorRepository
                .findByUserAndSenior_SeniorId(
                        subChild,
                        10L
                ))
                .willReturn(Optional.of(subRelation));

        Device device = Device.builder()
                .user(parent)
                .deviceIdentifier("device-1")
                .deviceName("Galaxy S24")
                .connectionStatus(DeviceStatus.ONLINE)
                .batteryLevel(72)
                .lastConnectedAt(LocalDateTime.now())
                .build();

        given(deviceRepository
                .findFirstByUserOrderByLastConnectedAtDescDeviceIdDesc(
                        parent
                ))
                .willReturn(Optional.of(device));

        given(userRepository.findByIdForUpdate(3L))
                .willReturn(Optional.of(parent));

        given(homeRepository
                .findAllByUserOrderByButtonOrderAsc(parent))
                .willReturn(List.of());

        given(homeRepository.saveAll(any()))
                .willAnswer(invocation ->
                        invocation.getArgument(0)
                );

        given(homeSettingRepository.findByUser(parent))
                .willReturn(Optional.empty());

        HomeResponse response =
                homeService.getHome(10L);

        assertThat(response.getSeniorProfile().getName())
                .isEqualTo("김영희");

        assertThat(response.getSeniorProfile().getRelation())
                .isEqualTo("GRANDPARENT");

        assertThat(response.getConnection().getConnected())
                .isTrue();

        assertThat(response.getConnection().getConnectionStatus())
                .isEqualTo(DeviceStatus.ONLINE);
    }

    @Test
    void getHomeReturnsSeniorProfileEvenWhenDeviceIsDisconnected() {
        Family family = Family.builder()
                .familyId(1L)
                .build();

        User primaryChild =
                createChild(
                        1L,
                        family,
                        ManagerType.PRIMARY
                );

        User parent = User.builder()
                .usersId(3L)
                .name("시니어")
                .role(Role.PARENT)
                .family(family)
                .build();

        Senior senior =
                createSenior(
                        10L,
                        family,
                        primaryChild,
                        parent
                );

        UserSenior relation =
                createUserSenior(
                        primaryChild,
                        senior,
                        SeniorRelation.MOTHER,
                        null
                );

        setCurrentUser(primaryChild);

        given(userSeniorRepository
                .findByUserAndSenior_SeniorId(
                        primaryChild,
                        10L
                ))
                .willReturn(Optional.of(relation));

        Device device = Device.builder()
                .user(parent)
                .deviceIdentifier("device-1")
                .deviceName("Galaxy S24")
                .connectionStatus(DeviceStatus.DISCONNECTED)
                .batteryLevel(72)
                .lastConnectedAt(LocalDateTime.now())
                .build();

        given(deviceRepository
                .findFirstByUserOrderByLastConnectedAtDescDeviceIdDesc(
                        parent
                ))
                .willReturn(Optional.of(device));

        HomeResponse response =
                homeService.getHome(10L);

        assertThat(response.getSeniorProfile().getName())
                .isEqualTo("김영희");

        assertThat(response.getSeniorProfile().getRelation())
                .isEqualTo("MOTHER");

        assertThat(response.getSeniorProfile().getBirth())
                .isEqualTo(LocalDate.of(1950, 1, 1));

        assertThat(response.getSeniorProfile().getAddress())
                .isEqualTo("서울시");

        assertThat(response.getSeniorProfile().getDetailAddress())
                .isEqualTo("101호");

        assertThat(response.getConnection().getConnected())
                .isFalse();

        assertThat(response.getConnection().getConnectionStatus())
                .isEqualTo(DeviceStatus.DISCONNECTED);
    }

    @Test
    void getHomeReturnsOfflineStatusWhenDeviceHasNotConnectedForMoreThan11Minutes() {
        Family family = Family.builder()
                .familyId(1L)
                .build();

        User primaryChild =
                createChild(
                        1L,
                        family,
                        ManagerType.PRIMARY
                );

        User parent = User.builder()
                .usersId(3L)
                .name("시니어")
                .role(Role.PARENT)
                .family(family)
                .build();

        Senior senior =
                createSenior(
                        10L,
                        family,
                        primaryChild,
                        parent
                );

        UserSenior relation =
                createUserSenior(
                        primaryChild,
                        senior,
                        SeniorRelation.MOTHER,
                        null
                );

        setCurrentUser(primaryChild);

        given(userSeniorRepository
                .findByUserAndSenior_SeniorId(
                        primaryChild,
                        10L
                ))
                .willReturn(Optional.of(relation));

        Device device = Device.builder()
                .user(parent)
                .deviceIdentifier("device-1")
                .deviceName("Galaxy S24")
                .connectionStatus(DeviceStatus.ONLINE)
                .batteryLevel(72)
                .lastConnectedAt(
                        LocalDateTime.now()
                                .minusMinutes(12)
                )
                .build();

        given(deviceRepository
                .findFirstByUserOrderByLastConnectedAtDescDeviceIdDesc(
                        parent
                ))
                .willReturn(Optional.of(device));

        HomeResponse response =
                homeService.getHome(10L);

        assertThat(response.getConnection().getConnected())
                .isFalse();

        assertThat(response.getConnection().getConnectionStatus())
                .isEqualTo(DeviceStatus.OFFLINE);
    }

    @Test
    void updateSeniorProfileUpdatesSelectedSeniorOnly() {
        Family family = Family.builder()
                .familyId(1L)
                .build();

        User child = createChild(
                1L,
                family,
                ManagerType.PRIMARY
        );

        Senior firstSenior = createSenior(
                10L,
                family,
                child
        );

        Senior secondSenior = createSenior(
                20L,
                family,
                child
        );

        UserSenior secondRelation = createUserSenior(
                child,
                secondSenior,
                SeniorRelation.GRANDPARENT,
                null
        );

        setCurrentUser(child);

        given(userSeniorRepository
                .findByUserAndSenior_SeniorId(
                        child,
                        20L
                ))
                .willReturn(Optional.of(secondRelation));

        given(userSeniorRepository.save(any(UserSenior.class)))
                .willAnswer(invocation ->
                        invocation.getArgument(0)
                );

        SeniorProfileUpdateResponse response =
                homeService.updateSeniorProfile(
                        20L,
                        createRequest(
                                "선택 시니어",
                                SeniorRelation.OTHER,
                                "외할머니"
                        )
                );

        assertThat(firstSenior.getName())
                .isEqualTo("김영희");

        assertThat(secondSenior.getName())
                .isEqualTo("선택 시니어");

        assertThat(secondRelation.getRelation())
                .isEqualTo(SeniorRelation.OTHER);

        assertThat(secondRelation.getCustomRelation())
                .isEqualTo("외할머니");

        assertThat(response.name())
                .isEqualTo("선택 시니어");
    }

    @Test
    void updateSeniorProfileRejectsUnmanagedSeniorId() {
        Family family = Family.builder()
                .familyId(1L)
                .build();

        User child = createChild(
                1L,
                family,
                ManagerType.PRIMARY
        );

        setCurrentUser(child);

        given(userSeniorRepository
                .findByUserAndSenior_SeniorId(
                        child,
                        999L
                ))
                .willReturn(Optional.empty());

        assertThatThrownBy(() ->
                homeService.updateSeniorProfile(
                        999L,
                        createRequest(
                                "접근 불가 시니어",
                                SeniorRelation.MOTHER,
                                null
                        )
                )
        )
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void getHomeReturnsOnlySelectedSeniorData() {
        Family family = Family.builder()
                .familyId(1L)
                .build();

        User child = createChild(
                1L,
                family,
                ManagerType.PRIMARY
        );

        User firstParent = User.builder()
                .usersId(3L)
                .name("첫 번째 시니어 계정")
                .role(Role.PARENT)
                .family(family)
                .build();

        User secondParent = User.builder()
                .usersId(4L)
                .name("두 번째 시니어 계정")
                .role(Role.PARENT)
                .family(family)
                .build();

        Senior firstSenior = createSenior(
                10L,
                family,
                child,
                firstParent
        );

        Senior secondSenior = createSenior(
                20L,
                family,
                child,
                secondParent
        );

        secondSenior.updateProfile(
                "두 번째 시니어",
                LocalDate.of(1960, 2, 2),
                "01099998888",
                "부산시",
                "202호",
                35.1796,
                129.0756
        );

        UserSenior firstRelation = createUserSenior(
                child,
                firstSenior,
                SeniorRelation.MOTHER,
                null
        );

        UserSenior secondRelation = createUserSenior(
                child,
                secondSenior,
                SeniorRelation.GRANDPARENT,
                null
        );

        setCurrentUser(child);

        given(userSeniorRepository
                .findByUserAndSenior_SeniorId(child, 20L))
                .willReturn(Optional.of(secondRelation));

        Device secondDevice = Device.builder()
                .user(secondParent)
                .deviceIdentifier("device-2")
                .deviceName("Galaxy S24")
                .connectionStatus(DeviceStatus.DISCONNECTED)
                .batteryLevel(80)
                .lastConnectedAt(LocalDateTime.now())
                .build();

        given(deviceRepository
                .findFirstByUserOrderByLastConnectedAtDescDeviceIdDesc(secondParent))
                .willReturn(Optional.of(secondDevice));

        HomeResponse response = homeService.getHome(20L);

        assertThat(response.getSeniorProfile().getName())
                .isEqualTo("두 번째 시니어");

        assertThat(response.getSeniorProfile().getRelation())
                .isEqualTo("GRANDPARENT");

        assertThat(response.getSeniorProfile().getBirth())
                .isEqualTo(LocalDate.of(1960, 2, 2));

        assertThat(response.getSeniorProfile().getAddress())
                .isEqualTo("부산시");

        assertThat(response.getSeniorProfile().getName())
                .isNotEqualTo(firstSenior.getName());

        assertThat(response.getSeniorProfile().getRelation())
                .isNotEqualTo(firstRelation.getRelation().name());
    }

    @Test
    void updateFontSizeUpdatesOnlySelectedSeniorSetting() {
        Family family = Family.builder()
                .familyId(1L)
                .build();

        User child = createChild(
                1L,
                family,
                ManagerType.PRIMARY
        );

        User firstParent = User.builder()
                .usersId(3L)
                .name("첫 번째 시니어")
                .role(Role.PARENT)
                .family(family)
                .build();

        User secondParent = User.builder()
                .usersId(4L)
                .name("두 번째 시니어")
                .role(Role.PARENT)
                .family(family)
                .build();

        Senior firstSenior = createSenior(
                10L,
                family,
                child,
                firstParent
        );

        Senior secondSenior = createSenior(
                20L,
                family,
                child,
                secondParent
        );

        UserSenior secondRelation = createUserSenior(
                child,
                secondSenior,
                SeniorRelation.GRANDPARENT,
                null
        );

        HomeSetting firstSetting = HomeSetting.builder()
                .user(firstParent)
                .fontSize(FontSize.MEDIUM)
                .build();

        HomeSetting secondSetting = HomeSetting.builder()
                .user(secondParent)
                .fontSize(FontSize.MEDIUM)
                .build();

        HomeFontSizeUpdateRequest request =
                org.mockito.Mockito.mock(HomeFontSizeUpdateRequest.class);

        given(request.getFontSize())
                .willReturn(FontSize.LARGE);

        setCurrentUser(child);

        given(userSeniorRepository
                .findByUserAndSenior_SeniorId(
                        child,
                        20L
                ))
                .willReturn(Optional.of(secondRelation));

        Device secondDevice = Device.builder()
                .user(secondParent)
                .deviceIdentifier("device-2")
                .deviceName("Galaxy S24")
                .connectionStatus(DeviceStatus.ONLINE)
                .batteryLevel(80)
                .lastConnectedAt(LocalDateTime.now())
                .build();

        given(deviceRepository
                .findFirstByUserOrderByLastConnectedAtDescDeviceIdDesc(
                        secondParent
                ))
                .willReturn(Optional.of(secondDevice));

        given(homeSettingRepository.findByUser(secondParent))
                .willReturn(Optional.of(secondSetting));

        given(homeSettingRepository.save(any(HomeSetting.class)))
                .willAnswer(invocation ->
                        invocation.getArgument(0)
                );

        homeService.updateFontSize(
                20L,
                request
        );

        assertThat(firstSetting.getFontSize())
                .isEqualTo(FontSize.MEDIUM);

        assertThat(secondSetting.getFontSize())
                .isEqualTo(FontSize.LARGE);
    }

    @Test
    void saveButtonsUpdatesOnlySelectedSeniorHome() {
        Family family = Family.builder()
                .familyId(1L)
                .build();

        User child = createChild(
                1L,
                family,
                ManagerType.PRIMARY
        );

        User firstParent = User.builder()
                .usersId(3L)
                .name("첫 번째 시니어")
                .role(Role.PARENT)
                .family(family)
                .build();

        User secondParent = User.builder()
                .usersId(4L)
                .name("두 번째 시니어")
                .role(Role.PARENT)
                .family(family)
                .build();

        Senior firstSenior = createSenior(
                10L,
                family,
                child,
                firstParent
        );

        Senior secondSenior = createSenior(
                20L,
                family,
                child,
                secondParent
        );

        UserSenior secondRelation = createUserSenior(
                child,
                secondSenior,
                SeniorRelation.GRANDPARENT,
                null
        );

        setCurrentUser(child);

        given(userSeniorRepository
                .findByUserAndSenior_SeniorId(
                        child,
                        20L
                ))
                .willReturn(Optional.of(secondRelation));

        Device secondDevice = Device.builder()
                .user(secondParent)
                .deviceIdentifier("device-2")
                .deviceName("Galaxy S24")
                .connectionStatus(DeviceStatus.ONLINE)
                .batteryLevel(80)
                .lastConnectedAt(LocalDateTime.now())
                .build();

        given(deviceRepository
                .findFirstByUserOrderByLastConnectedAtDescDeviceIdDesc(
                        secondParent
                ))
                .willReturn(Optional.of(secondDevice));

        given(homeSettingRepository.findByUser(secondParent))
                .willReturn(Optional.empty());

        List<HomeButtonSaveRequest.ButtonRequest> buttons = List.of(
                new HomeButtonSaveRequest.ButtonRequest(
                        1,
                        "전화",
                        ActionType.DEFAULT,
                        "PHONE",
                        null
                ),
                new HomeButtonSaveRequest.ButtonRequest(
                        2,
                        "메시지",
                        ActionType.DEFAULT,
                        "MESSAGE",
                        null
                ),
                new HomeButtonSaveRequest.ButtonRequest(
                        3,
                        "카메라",
                        ActionType.DEFAULT,
                        "CAMERA",
                        null
                ),
                new HomeButtonSaveRequest.ButtonRequest(
                        4,
                        "사진",
                        ActionType.DEFAULT,
                        "PHOTO",
                        null
                ),
                new HomeButtonSaveRequest.ButtonRequest(
                        5,
                        "설정",
                        ActionType.DEFAULT,
                        "SETTINGS",
                        null
                ),
                new HomeButtonSaveRequest.ButtonRequest(
                        6,
                        "복약",
                        ActionType.DEFAULT,
                        "MEDICATION",
                        null
                ),
                new HomeButtonSaveRequest.ButtonRequest(
                        7,
                        "긴급알림",
                        ActionType.DEFAULT,
                        "EMERGENCY",
                        null
                ),
                new HomeButtonSaveRequest.ButtonRequest(
                        8,
                        "유튜브",
                        ActionType.APP,
                        "YOUTUBE",
                        "com.google.android.youtube"
                )
        );

        HomeButtonSaveRequest request =
                new HomeButtonSaveRequest(
                        null,
                        buttons
                );

        homeService.saveButtons(
                20L,
                request
        );

        org.mockito.Mockito.verify(homeRepository)
                .deleteAllByUser(secondParent);

        org.mockito.Mockito.verify(homeRepository)
                .saveAllAndFlush(any());

        org.mockito.Mockito.verify(homeSettingRepository)
                .findByUser(secondParent);

        org.mockito.Mockito.verify(homeWebSocketService)
                .notifyHomeUpdated(4L);

        org.mockito.Mockito.verify(
                homeRepository,
                org.mockito.Mockito.never()
        ).deleteAllByUser(firstParent);

        org.mockito.Mockito.verify(
                homeSettingRepository,
                org.mockito.Mockito.never()
        ).findByUser(firstParent);

        assertThat(firstSenior.getSeniorId())
                .isEqualTo(10L);

        assertThat(secondSenior.getSeniorId())
                .isEqualTo(20L);
    }

    @Test
    void getTodayHospitalSchedulesUsesOnlySelectedSenior() {
        Family family = Family.builder()
                .familyId(1L)
                .build();

        User child = createChild(
                1L,
                family,
                ManagerType.PRIMARY
        );

        User firstParent = User.builder()
                .usersId(3L)
                .name("첫 번째 시니어")
                .role(Role.PARENT)
                .family(family)
                .build();

        User secondParent = User.builder()
                .usersId(4L)
                .name("두 번째 시니어")
                .role(Role.PARENT)
                .family(family)
                .build();

        Senior firstSenior = createSenior(
                10L,
                family,
                child,
                firstParent
        );

        Senior secondSenior = createSenior(
                20L,
                family,
                child,
                secondParent
        );

        UserSenior secondRelation = createUserSenior(
                child,
                secondSenior,
                SeniorRelation.GRANDPARENT,
                null
        );

        setCurrentUser(child);

        given(userSeniorRepository
                .findByUserAndSenior_SeniorId(
                        child,
                        20L
                ))
                .willReturn(Optional.of(secondRelation));

        LocalDate today = LocalDate.now();

        given(hospitalRepository
                .findByUserAndScheduleDateBetweenOrderByScheduleDateAscScheduleTimeAsc(
                        secondParent,
                        today,
                        today
                ))
                .willReturn(List.of());

        var response =
                homeService.getTodayHospitalSchedules(20L);

        assertThat(response)
                .isEmpty();

        org.mockito.Mockito.verify(hospitalRepository)
                .findByUserAndScheduleDateBetweenOrderByScheduleDateAscScheduleTimeAsc(
                        secondParent,
                        today,
                        today
                );

        org.mockito.Mockito.verify(
                hospitalRepository,
                org.mockito.Mockito.never()
        ).findByUserAndScheduleDateBetweenOrderByScheduleDateAscScheduleTimeAsc(
                firstParent,
                today,
                today
        );

        assertThat(firstSenior.getSeniorId())
                .isEqualTo(10L);

        assertThat(secondSenior.getSeniorId())
                .isEqualTo(20L);
    }

    private SeniorProfileUpdateRequest createRequest(
            String name,
            SeniorRelation relation,
            String customRelation
    ) {
        return new SeniorProfileUpdateRequest(
                name,
                relation,
                customRelation,
                LocalDate.of(1950, 1, 1),
                "010-1234-5678",
                "서울시",
                "101호",
                37.5665,
                126.9780
        );
    }

    private Senior createSenior(Long seniorId, Family family, User registeredBy) {
        return Senior.builder()
                .seniorId(seniorId)
                .name("김영희")
                .birth(LocalDate.of(1950, 1, 1))
                .phoneNumber("01012345678")
                .address("서울시")
                .detailAddress("101호")
                .latitude(35.1796)
                .longitude(129.0756)
                .family(family)
                .registeredBy(registeredBy)
                .build();
    }
    private Senior createSenior(
            Long seniorId,
            Family family,
            User registeredBy,
            User parentUser
    ) {
        return Senior.builder()
                .seniorId(seniorId)
                .name("김영희")
                .birth(LocalDate.of(1950, 1, 1))
                .phoneNumber("01012345678")
                .address("서울시")
                .detailAddress("101호")
                .latitude(35.1796)
                .longitude(129.0756)
                .family(family)
                .registeredBy(registeredBy)
                .parentUser(parentUser)
                .build();
    }

    private UserSenior createUserSenior(
            User user,
            Senior senior,
            SeniorRelation relation,
            String customRelation
    ) {
        return UserSenior.builder()
                .user(user)
                .senior(senior)
                .relation(relation)
                .customRelation(customRelation)
                .build();
    }

    private User createChild(Long usersId, Family family, ManagerType managerType) {
        return User.builder()
                .usersId(usersId)
                .name("자녀" + usersId)
                .role(Role.CHILD)
                .managerType(managerType)
                .family(family)
                .build();
    }

    private void setCurrentUser(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null)
        );
    }
}
