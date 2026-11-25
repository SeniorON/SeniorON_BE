
package com.example.senioron.domain.home.service;

import com.example.senioron.domain.device.dto.response.DeviceDetailResponse;
import com.example.senioron.domain.device.entity.Device;
import com.example.senioron.domain.device.entity.DeviceStatus;
import com.example.senioron.domain.device.repository.DeviceRepository;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyMember;
import com.example.senioron.domain.family.repository.FamilyMemberRepository;
import com.example.senioron.domain.family.repository.FamilyRepository;
import com.example.senioron.domain.home.dto.request.HomeButtonSaveRequest;
import com.example.senioron.domain.home.dto.request.HomeFontSizeUpdateRequest;
import com.example.senioron.domain.home.entity.*;
import com.example.senioron.domain.home.repository.ButtonOptionRepository;
import com.example.senioron.domain.home.repository.HomeRepository;
import com.example.senioron.domain.home.repository.HomeSettingRepository;
import com.example.senioron.domain.hospital.repository.HospitalRepository;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.repository.SeniorRepository;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.RefreshToken;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.RefreshTokenRepository;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import com.example.senioron.domain.home.dto.response.HomeResponse;
import com.example.senioron.domain.home.entity.HomeSetting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.mockito.BDDMockito.given;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

class HomeServiceOfflineTest {

    private final HomeRepository homeRepository = mock(HomeRepository.class);
    private final ButtonOptionRepository buttonOptionRepository = mock(ButtonOptionRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final HospitalRepository hospitalRepository = mock(HospitalRepository.class);
    private final DeviceRepository deviceRepository = mock(DeviceRepository.class);
    private final SeniorRepository seniorRepository = mock(SeniorRepository.class);
    private final HomeSettingRepository homeSettingRepository = mock(HomeSettingRepository.class);
    private final FamilyRepository familyRepository = mock(FamilyRepository.class);
    private final FamilyMemberRepository familyMemberRepository = mock(FamilyMemberRepository.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final HomeWebSocketService homeWebSocketService = mock(HomeWebSocketService.class);

    private HomeService homeService;
    private User child;
    private User seniorUser;

    @BeforeEach
    void setUp() {
        homeService = new HomeService(
                homeRepository,
                buttonOptionRepository,
                userRepository,
                hospitalRepository,
                deviceRepository,
                seniorRepository,
                homeSettingRepository,
                familyRepository,
                familyMemberRepository,
                refreshTokenRepository,
                homeWebSocketService
        );

        child = User.builder()
                .usersId(1L)
                .role(Role.CHILD)
                .build();

        seniorUser = User.builder()
                .usersId(2L)
                .role(Role.PARENT)
                .build();

        Family family = Family.builder()
                .familyId(1L)
                .build();

        Senior senior = Senior.builder()
                .seniorId(10L)
                .family(family)
                .parentUser(seniorUser)
                .registeredBy(child)
                .build();

        FamilyMember familyMember = FamilyMember.builder()
                .user(child)
                .family(family)
                .managerType(ManagerType.PRIMARY)
                .build();

        given(seniorRepository.findById(10L))
                .willReturn(Optional.of(senior));

        given(familyMemberRepository.findByUserAndFamily(child, family))
                .willReturn(Optional.of(familyMember));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(child, null)
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void mockDevice(DeviceStatus status) {
        Device device = Device.builder()
                .user(seniorUser)
                .connectionStatus(status)
                .lastConnectedAt(LocalDateTime.now().minusMinutes(20))
                .batteryLevel(80)
                .build();

        given(deviceRepository.findFirstByUserOrderByLastConnectedAtDescDeviceIdDesc(seniorUser))
                .willReturn(Optional.of(device));
    }

    private HomeFontSizeUpdateRequest fontSizeRequest() {
        HomeFontSizeUpdateRequest request = mock(HomeFontSizeUpdateRequest.class);
        given(request.getFontSize()).willReturn(FontSize.LARGE);
        return request;
    }


    private HomeButtonSaveRequest buttonsRequest() {
        List<HomeButtonSaveRequest.ButtonRequest> buttons = List.of(
                new HomeButtonSaveRequest.ButtonRequest(
                        1, "설정", ActionType.DEFAULT, "SETTINGS", null
                ),
                new HomeButtonSaveRequest.ButtonRequest(
                        2, "전화", ActionType.DEFAULT, "PHONE", null
                ),
                new HomeButtonSaveRequest.ButtonRequest(
                        3, "메시지", ActionType.DEFAULT, "MESSAGE", null
                ),
                new HomeButtonSaveRequest.ButtonRequest(
                        4, "카메라", ActionType.DEFAULT, "CAMERA", null
                ),
                new HomeButtonSaveRequest.ButtonRequest(
                        5, "사진", ActionType.DEFAULT, "PHOTO", null
                ),
                new HomeButtonSaveRequest.ButtonRequest(
                        6, "말벗", ActionType.DEFAULT, "COMPANION", null
                ),
                new HomeButtonSaveRequest.ButtonRequest(
                        7, "복약", ActionType.DEFAULT, "MEDICATION", null
                ),
                new HomeButtonSaveRequest.ButtonRequest(
                        8, "긴급 알림", ActionType.DEFAULT, "EMERGENCY", null
                )
        );

        return new HomeButtonSaveRequest(10L, null, buttons);
    }


    @Test
    void updateFontSizeSucceedsWhenDeviceOffline() {
        mockDevice(DeviceStatus.OFFLINE);

        homeService.updateFontSize(10L, fontSizeRequest());

        verify(homeSettingRepository).save(any());
        verify(homeWebSocketService).notifyHomeUpdated(2L);
    }

    @Test
    void updateFontSizeFailsWhenDeviceDisconnected() {
        mockDevice(DeviceStatus.DISCONNECTED);

        assertThatThrownBy(() ->
                homeService.updateFontSize(10L, fontSizeRequest())
        )
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.DEVICE_NOT_CONNECTED);

        verify(homeSettingRepository, never()).save(any());
    }

    @Test
    void saveButtonsSucceedsWhenDeviceOffline() {
        mockDevice(DeviceStatus.OFFLINE);

        try {
            homeService.saveButtons(10L, buttonsRequest());
        } catch (BusinessException e) {
            System.out.println("발생한 ErrorCode: " + e.getCode());
            throw e;
        }

        verify(homeRepository).saveAllAndFlush(any());
        verify(homeSettingRepository).save(any());
        verify(homeWebSocketService).notifyHomeUpdated(2L);
    }

    @Test
    void saveButtonsFailsWhenDeviceDisconnected() {
        mockDevice(DeviceStatus.DISCONNECTED);

        assertThatThrownBy(() ->
                homeService.saveButtons(10L, buttonsRequest())
        )
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.DEVICE_NOT_CONNECTED);

        verify(homeRepository, never()).saveAllAndFlush(any());
    }



    @Test
    void getHomeKeepsSavedSettingsWhenDeviceOffline() {
        mockDevice(DeviceStatus.OFFLINE);

        // 홈 버튼 조회에 필요한 사용자 잠금 조회
        given(userRepository.findByIdForUpdate(seniorUser.getUsersId()))
                .willReturn(Optional.of(seniorUser));

        // 기존에 저장된 홈 버튼
        Home savedButton = mock(Home.class);
        given(savedButton.getButtonOrder()).willReturn(1);
        given(savedButton.getButtonName()).willReturn("전화");
        given(savedButton.getActionType()).willReturn(ActionType.DEFAULT);
        given(savedButton.getActionValue()).willReturn("PHONE");

        given(homeRepository.findAllByUserOrderByButtonOrderAsc(seniorUser))
                .willReturn(List.of(savedButton));

        // 기존에 저장된 글자 크기
        HomeSetting setting = mock(HomeSetting.class);
        given(setting.getFontSize()).willReturn(FontSize.LARGE);

        given(homeSettingRepository.findByUser(seniorUser))
                .willReturn(Optional.of(setting));

        // 홈 조회
        HomeResponse response = homeService.getHome(10L);

        // OFFLINE이어도 저장된 설정 유지
        assertEquals(FontSize.LARGE, response.getFontSize());
        assertEquals(1, response.getButtons().size());
        assertEquals("PHONE", response.getButtons().get(0).getActionValue());

        // 기존 버튼을 새로 생성하지 않았는지 확인
        verify(homeRepository, never()).saveAll(any());
    }


    @Test
    void getHomeReturnsDefaultSettingsWhenDeviceDisconnected() {
        mockDevice(DeviceStatus.DISCONNECTED);

        HomeResponse response = homeService.getHome(10L);

        assertEquals(FontSize.MEDIUM, response.getFontSize());
        assertEquals(false, response.getMusicCard().getEnabled());

        verify(homeSettingRepository, never()).findByUser(seniorUser);
    }


    @Test
    void deviceIsOnlineWhenLastConnected17MinutesAgo() {
        assertDeviceStatus(
                LocalDateTime.now().minusMinutes(17),
                DeviceStatus.ONLINE
        );
    }

    @Test
    void deviceIsOfflineWhenLastConnected19MinutesAgo() {
        assertDeviceStatus(
                LocalDateTime.now().minusMinutes(19),
                DeviceStatus.OFFLINE
        );
    }

    @Test
    void deviceIsOfflineWhenLastConnectedAtIsNull() {
        assertDeviceStatus(
                null,
                DeviceStatus.OFFLINE
        );
    }

    private void assertDeviceStatus(
            LocalDateTime lastConnectedAt,
            DeviceStatus expectedStatus
    ) {
        Device device = mock(Device.class);

        given(device.getConnectionStatus())
                .willReturn(DeviceStatus.ONLINE);
        given(device.getDeviceIdentifier())
                .willReturn("test-device");
        given(device.getLastConnectedAt())
                .willReturn(lastConnectedAt);

        given(deviceRepository
                .findFirstByUserOrderByLastConnectedAtDescDeviceIdDesc(seniorUser))
                .willReturn(Optional.of(device));

        RefreshToken refreshToken = mock(RefreshToken.class);

        given(refreshToken.isExpired(any(LocalDateTime.class)))
                .willReturn(false);

        given(refreshTokenRepository.findByUserAndDeviceIdentifier(
                seniorUser,
                "test-device"
        )).willReturn(Optional.of(refreshToken));

        DeviceDetailResponse response =
                homeService.getDeviceDetail(10L);

        assertEquals(expectedStatus, response.connectionStatus());
    }

}
