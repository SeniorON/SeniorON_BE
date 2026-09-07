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
import com.example.senioron.domain.user.repository.UserRepository;
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
                homeWebSocketService
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void updateSeniorProfileCreatesFamilySeniorAndUserSenior() {
        Family family = Family.builder().familyId(1L).build();
        User primaryChild = createChild(1L, family, ManagerType.PRIMARY);
        setCurrentUser(primaryChild);
        given(familyRepository.findByIdForUpdate(1L)).willReturn(Optional.of(family));
        given(seniorRepository.findFirstByFamily(family)).willReturn(Optional.empty());
        given(seniorRepository.saveAndFlush(any(Senior.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(userSeniorRepository.findByUserAndSenior(any(User.class), any(Senior.class)))
                .willReturn(Optional.empty());
        given(userSeniorRepository.save(any(UserSenior.class))).willAnswer(invocation -> invocation.getArgument(0));

        SeniorProfileUpdateResponse response = homeService.updateSeniorProfile(
                createRequest("김영희", SeniorRelation.MOTHER, null)
        );

        assertThat(response.name()).isEqualTo("김영희");
        assertThat(response.relation()).isEqualTo(SeniorRelation.MOTHER);
        assertThat(response.latitude()).isEqualTo(37.5665);
        assertThat(response.longitude()).isEqualTo(126.9780);
    }

    @Test
    void updateSeniorProfileUpdatesOnlyCurrentUsersRelation() {
        Family family = Family.builder().familyId(1L).build();
        User primaryChild = createChild(1L, family, ManagerType.PRIMARY);
        User subChild = createChild(2L, family, ManagerType.SUB);
        Senior senior = createSenior(10L, family, primaryChild);
        UserSenior primaryRelation = createUserSenior(primaryChild, senior, SeniorRelation.MOTHER, null);
        UserSenior subRelation = createUserSenior(subChild, senior, SeniorRelation.GRANDPARENT, null);
        setCurrentUser(subChild);
        given(familyRepository.findByIdForUpdate(1L)).willReturn(Optional.of(family));
        given(seniorRepository.findFirstByFamily(family)).willReturn(Optional.of(senior));
        given(userSeniorRepository.findByUserAndSenior(subChild, senior)).willReturn(Optional.of(subRelation));
        given(userSeniorRepository.save(any(UserSenior.class))).willAnswer(invocation -> invocation.getArgument(0));

        SeniorProfileUpdateResponse response = homeService.updateSeniorProfile(
                createRequest("김영자", SeniorRelation.OTHER, "외할머니")
        );

        assertThat(response.name()).isEqualTo("김영자");
        assertThat(response.relation()).isEqualTo(SeniorRelation.OTHER);
        assertThat(subRelation.getCustomRelation()).isEqualTo("외할머니");
        assertThat(primaryRelation.getRelation()).isEqualTo(SeniorRelation.MOTHER);
        assertThat(senior.getName()).isEqualTo("김영자");
        assertThat(senior.getLatitude()).isEqualTo(37.5665);
        assertThat(senior.getLongitude()).isEqualTo(126.9780);
        assertThat(response.latitude()).isEqualTo(37.5665);
        assertThat(response.longitude()).isEqualTo(126.9780);
    }

    @Test
    void updateSeniorProfileRejectsSeniorFromDifferentFamily() {
        Family family = Family.builder().familyId(1L).build();
        Family otherFamily = Family.builder().familyId(2L).build();
        User child = createChild(1L, family, ManagerType.PRIMARY);
        Senior otherFamilySenior = createSenior(10L, otherFamily, child);
        setCurrentUser(child);
        given(familyRepository.findByIdForUpdate(1L)).willReturn(Optional.of(family));
        given(seniorRepository.findFirstByFamily(family)).willReturn(Optional.of(otherFamilySenior));
        given(userSeniorRepository.findByUserAndSenior(child, otherFamilySenior)).willReturn(Optional.empty());

        assertThatThrownBy(() -> homeService.updateSeniorProfile(
                createRequest("김영희", SeniorRelation.MOTHER, null)
        ))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void updateSeniorProfileConvertsUniqueViolationToSeniorAlreadyExists() {
        Family family = Family.builder().familyId(1L).build();
        User primaryChild = createChild(1L, family, ManagerType.PRIMARY);
        setCurrentUser(primaryChild);
        given(familyRepository.findByIdForUpdate(1L)).willReturn(Optional.of(family));
        given(seniorRepository.findFirstByFamily(family)).willReturn(Optional.empty());
        given(seniorRepository.saveAndFlush(any(Senior.class)))
                .willThrow(new DataIntegrityViolationException("duplicate family senior"));

        assertThatThrownBy(() -> homeService.updateSeniorProfile(
                createRequest("김영희", SeniorRelation.MOTHER, null)
        ))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.SENIOR_ALREADY_EXISTS);
    }

    @Test
    void getHomeReturnsCurrentUsersSeniorRelation() {
        Family family = Family.builder().familyId(1L).build();
        User subChild = createChild(2L, family, ManagerType.SUB);
        User primaryChild = createChild(1L, family, ManagerType.PRIMARY);
        Senior senior = createSenior(10L, family, primaryChild);
        UserSenior subRelation = createUserSenior(subChild, senior, SeniorRelation.GRANDPARENT, null);
        setCurrentUser(subChild);
        given(userRepository.findByFamilyAndUsersIdNotAndRole(family, 2L, Role.CHILD))
                .willReturn(List.of(primaryChild));
        given(userRepository.findByIdForUpdate(1L))
                .willReturn(Optional.of(primaryChild));
        User parent = User.builder()
                .usersId(3L)
                .name("시니어")
                .role(Role.PARENT)
                .family(family)
                .build();

        given(userRepository.findByFamilyAndUsersIdNotAndRole(family, 2L, Role.PARENT))
                .willReturn(List.of(parent));
        given(userSeniorRepository.findFirstByUserAndSenior_Family(subChild, family))
                .willReturn(Optional.of(subRelation));
        Device device = Device.builder()
                .user(parent)
                .deviceIdentifier("device-1")
                .deviceName("Galaxy S24")
                .connectionStatus(DeviceStatus.ONLINE)
                .batteryLevel(72)
                .lastConnectedAt(LocalDateTime.now())
                .build();

        given(deviceRepository.findFirstByUserOrderByLastConnectedAtDescDeviceIdDesc(parent))
                .willReturn(Optional.of(device));
        given(homeRepository.findAllByUserOrderByButtonOrderAsc(primaryChild)).willReturn(List.of());
        given(homeSettingRepository.findByUser(primaryChild)).willReturn(Optional.empty());

        HomeResponse response = homeService.getHome();

        assertThat(response.getSeniorProfile().getName()).isEqualTo("김영희");
        assertThat(response.getSeniorProfile().getRelation()).isEqualTo("GRANDPARENT");

        assertThat(response.getConnection().getConnected()).isTrue();
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

        Senior senior =
                createSenior(
                        10L,
                        family,
                        primaryChild
                );

        UserSenior relation =
                createUserSenior(
                        primaryChild,
                        senior,
                        SeniorRelation.MOTHER,
                        null
                );

        User parent = User.builder()
                .usersId(3L)
                .name("시니어")
                .role(Role.PARENT)
                .family(family)
                .build();

        setCurrentUser(primaryChild);

        given(userRepository.findByFamilyAndUsersIdNotAndRole(
                family,
                1L,
                Role.PARENT
        )).willReturn(List.of(parent));

        given(userSeniorRepository.findFirstByUserAndSenior_Family(
                primaryChild,
                family
        )).willReturn(Optional.of(relation));

        Device device = Device.builder()
                .user(parent)
                .deviceIdentifier("device-1")
                .deviceName("Galaxy S24")
                .connectionStatus(DeviceStatus.DISCONNECTED)
                .batteryLevel(72)
                .lastConnectedAt(LocalDateTime.now())
                .build();

        given(deviceRepository
                .findFirstByUserOrderByLastConnectedAtDescDeviceIdDesc(parent))
                .willReturn(Optional.of(device));

        HomeResponse response =
                homeService.getHome();

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

        assertThat(response.getConnection().getConnected()).isFalse();

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

        Senior senior =
                createSenior(
                        10L,
                        family,
                        primaryChild
                );

        UserSenior relation =
                createUserSenior(
                        primaryChild,
                        senior,
                        SeniorRelation.MOTHER,
                        null
                );

        User parent = User.builder()
                .usersId(3L)
                .name("시니어")
                .role(Role.PARENT)
                .family(family)
                .build();

        setCurrentUser(primaryChild);

        given(userRepository.findByFamilyAndUsersIdNotAndRole(
                family,
                1L,
                Role.PARENT
        )).willReturn(List.of(parent));

        given(userSeniorRepository.findFirstByUserAndSenior_Family(
                primaryChild,
                family
        )).willReturn(Optional.of(relation));

        Device device = Device.builder()
                .user(parent)
                .deviceIdentifier("device-1")
                .deviceName("Galaxy S24")
                .connectionStatus(DeviceStatus.ONLINE)
                .batteryLevel(72)
                .lastConnectedAt(LocalDateTime.now().minusMinutes(12))
                .build();

        given(deviceRepository
                .findFirstByUserOrderByLastConnectedAtDescDeviceIdDesc(parent))
                .willReturn(Optional.of(device));

        HomeResponse response =
                homeService.getHome();

        assertThat(response.getConnection().getConnected())
                .isFalse();

        assertThat(response.getConnection().getConnectionStatus())
                .isEqualTo(DeviceStatus.OFFLINE);
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
