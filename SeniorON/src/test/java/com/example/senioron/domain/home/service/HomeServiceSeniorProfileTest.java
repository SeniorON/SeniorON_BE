package com.example.senioron.domain.home.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.BDDMockito.given;

import com.example.senioron.domain.device.repository.DeviceRepository;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyMember;
import com.example.senioron.domain.family.repository.FamilyMemberRepository;
import com.example.senioron.domain.family.repository.FamilyRepository;
import com.example.senioron.domain.home.dto.request.SeniorProfileUpdateRequest;
import com.example.senioron.domain.home.repository.ButtonOptionRepository;
import com.example.senioron.domain.home.repository.HomeRepository;
import com.example.senioron.domain.home.repository.HomeSettingRepository;
import com.example.senioron.domain.hospital.repository.HospitalRepository;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.entity.SeniorRelation;
import com.example.senioron.domain.senior.repository.SeniorRepository;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.RefreshTokenRepository;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class HomeServiceSeniorProfileTest {

    private final HomeRepository homeRepository =
            org.mockito.Mockito.mock(HomeRepository.class);
    private final ButtonOptionRepository buttonOptionRepository =
            org.mockito.Mockito.mock(ButtonOptionRepository.class);
    private final UserRepository userRepository =
            org.mockito.Mockito.mock(UserRepository.class);
    private final HospitalRepository hospitalRepository =
            org.mockito.Mockito.mock(HospitalRepository.class);
    private final DeviceRepository deviceRepository =
            org.mockito.Mockito.mock(DeviceRepository.class);
    private final SeniorRepository seniorRepository =
            org.mockito.Mockito.mock(SeniorRepository.class);
    private final HomeSettingRepository homeSettingRepository =
            org.mockito.Mockito.mock(HomeSettingRepository.class);
    private final FamilyRepository familyRepository =
            org.mockito.Mockito.mock(FamilyRepository.class);
    private final RefreshTokenRepository refreshTokenRepository =
            org.mockito.Mockito.mock(RefreshTokenRepository.class);
    private final HomeWebSocketService homeWebSocketService =
            org.mockito.Mockito.mock(HomeWebSocketService.class);

    private HomeService homeService;


    private final FamilyMemberRepository familyMemberRepository =
            org.mockito.Mockito.mock(FamilyMemberRepository.class);

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
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void updateSeniorProfileUsesFamilySeniorRelationship() {
        Family family = Family.builder().familyId(1L).build();
        User child = createChild(1L);
        Senior senior = Senior.builder()
                .seniorId(10L)
                .name("김영희")
                .birth(LocalDate.of(1950, 1, 1))
                .phoneNumber("01012345678")
                .family(family)
                .registeredBy(child)
                .build();

        setCurrentUser(child);

        given(seniorRepository.findById(10L))
                .willReturn(Optional.of(senior));

        FamilyMember familyMember = FamilyMember.builder()
                .user(child)
                .family(family)
                .managerType(ManagerType.PRIMARY)
                .build();

        given(familyMemberRepository.findByUserAndFamily(child, family))
                .willReturn(Optional.of(familyMember));

        var response = homeService.updateSeniorProfile(
                10L,
                new SeniorProfileUpdateRequest(
                        10L,
                        "박영희",
                        SeniorRelation.OTHER,
                        "친할머니",
                        LocalDate.of(1951, 2, 3),
                        "010-9999-8888",
                        "서울시",
                        "101호",
                        37.1,
                        127.1
                )
        );

        assertThat(response.seniorId()).isEqualTo(10L);
        assertThat(response.name()).isEqualTo("박영희");
        assertThat(response.relation()).isEqualTo(SeniorRelation.OTHER);
        assertThat(senior.getRelation()).isEqualTo(SeniorRelation.OTHER);
        assertThat(senior.getCustomRelation()).isEqualTo("친할머니");
        assertThat(response.customRelation()).isEqualTo("친할머니");
        assertThat(senior.getPhoneNumber()).isEqualTo("01099998888");
    }

    @Test
    void updateSeniorProfileRejectsSeniorFromDifferentFamily() {
        Family otherFamily = Family.builder().familyId(2L).build();
        User child = createChild(1L);
        Senior senior = Senior.builder()
                .seniorId(10L)
                .name("김영희")
                .birth(LocalDate.of(1950, 1, 1))
                .phoneNumber("01012345678")
                .family(otherFamily)
                .registeredBy(child)
                .build();

        setCurrentUser(child);

        given(seniorRepository.findById(10L))
                .willReturn(Optional.of(senior));

        given(familyMemberRepository.findByUserAndFamily(child, otherFamily))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> homeService.updateSeniorProfile(
                10L,
                new SeniorProfileUpdateRequest(
                        10L,
                        "박영희",
                        SeniorRelation.MOTHER,
                        null,
                        LocalDate.of(1951, 2, 3),
                        "010-9999-8888",
                        "서울시",
                        "101호",
                        37.1,
                        127.1
                )
        ))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    private User createChild(Long usersId) {
        return User.builder()
                .usersId(usersId)
                .name("...")
                .role(Role.CHILD)
                .build();
    }

    private void setCurrentUser(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null)
        );
    }
}
