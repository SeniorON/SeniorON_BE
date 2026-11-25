
package com.example.senioron.domain.home.service;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.BDDMockito.given;

import com.example.senioron.domain.device.repository.DeviceRepository;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyMember;
import com.example.senioron.domain.family.repository.FamilyMemberRepository;
import com.example.senioron.domain.family.repository.FamilyRepository;
import com.example.senioron.domain.home.dto.request.SeniorRelationUpdateRequest;
import com.example.senioron.domain.home.repository.*;
import com.example.senioron.domain.hospital.repository.HospitalRepository;
import com.example.senioron.domain.senior.entity.*;
import com.example.senioron.domain.senior.repository.SeniorRepository;
import com.example.senioron.domain.user.entity.*;
import com.example.senioron.domain.user.repository.*;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class HomeServiceSeniorRelationTest {

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
    private final FamilyMemberRepository familyMemberRepository =
            org.mockito.Mockito.mock(FamilyMemberRepository.class);
    private final RefreshTokenRepository refreshTokenRepository =
            org.mockito.Mockito.mock(RefreshTokenRepository.class);
    private final HomeWebSocketService homeWebSocketService =
            org.mockito.Mockito.mock(HomeWebSocketService.class);

    private HomeService homeService;

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
    void updateSeniorRelationOnlyChangesCurrentMember() {
        Family family = Family.builder().familyId(1L).build();
        User primary = createChild(1L);
        User secondary = createChild(2L);

        Senior senior = createSenior(family, primary);

        FamilyMember primaryMember = FamilyMember.builder()
                .user(primary)
                .family(family)
                .managerType(ManagerType.PRIMARY)
                .build();

        FamilyMember secondaryMember = FamilyMember.builder()
                .user(secondary)
                .family(family)
                .managerType(ManagerType.SUB)
                .build();

        primaryMember.updateRelation(SeniorRelation.FATHER, null);
        secondaryMember.updateRelation(SeniorRelation.MOTHER, null);

        setCurrentUser(primary);

        given(seniorRepository.findById(10L))
                .willReturn(Optional.of(senior));

        given(familyMemberRepository.findByUserAndFamily(primary, family))
                .willReturn(Optional.of(primaryMember));

        var response = homeService.updateSeniorRelation(
                new SeniorRelationUpdateRequest(
                        10L,
                        SeniorRelation.OTHER,
                        "친할머니"
                )
        );

        assertThat(response.relation()).isEqualTo(SeniorRelation.OTHER);
        assertThat(primaryMember.getRelation()).isEqualTo(SeniorRelation.OTHER);
        assertThat(primaryMember.getCustomRelation()).isEqualTo("친할머니");

        assertThat(secondaryMember.getRelation()).isEqualTo(SeniorRelation.MOTHER);

        assertThat(senior.getName()).isEqualTo("김영희");
        assertThat(senior.getPhoneNumber()).isEqualTo("01012345678");
        assertThat(senior.getLatitude()).isEqualTo(37.5);
        assertThat(senior.getLongitude()).isEqualTo(127.0);
    }

    @Test
    void updateSeniorRelationWorksForSecondaryMember() {
        Family family = Family.builder().familyId(1L).build();
        User primary = createChild(1L);
        User secondary = createChild(2L);

        Senior senior = createSenior(family, primary);

        FamilyMember primaryMember = FamilyMember.builder()
                .user(primary)
                .family(family)
                .managerType(ManagerType.PRIMARY)
                .build();

        FamilyMember secondaryMember = FamilyMember.builder()
                .user(secondary)
                .family(family)
                .managerType(ManagerType.SUB)
                .build();

        primaryMember.updateRelation(SeniorRelation.MOTHER, null);

        setCurrentUser(secondary);

        given(seniorRepository.findById(10L))
                .willReturn(Optional.of(senior));

        given(familyMemberRepository.findByUserAndFamily(secondary, family))
                .willReturn(Optional.of(secondaryMember));

        homeService.updateSeniorRelation(
                new SeniorRelationUpdateRequest(
                        10L,
                        SeniorRelation.FATHER,
                        null
                )
        );

        assertThat(secondaryMember.getRelation()).isEqualTo(SeniorRelation.FATHER);
        assertThat(primaryMember.getRelation()).isEqualTo(SeniorRelation.MOTHER);
    }

    @Test
    void updateSeniorRelationRejectsDifferentFamily() {
        Family family = Family.builder().familyId(2L).build();
        User child = createChild(1L);
        Senior senior = createSenior(family, child);

        setCurrentUser(child);

        given(seniorRepository.findById(10L))
                .willReturn(Optional.of(senior));

        given(familyMemberRepository.findByUserAndFamily(child, family))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> homeService.updateSeniorRelation(
                new SeniorRelationUpdateRequest(
                        10L,
                        SeniorRelation.MOTHER,
                        null
                )
        ))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    private Senior createSenior(Family family, User registeredBy) {
        return Senior.builder()
                .seniorId(10L)
                .name("김영희")
                .birth(LocalDate.of(1950, 1, 1))
                .phoneNumber("01012345678")
                .latitude(37.5)
                .longitude(127.0)
                .family(family)
                .registeredBy(registeredBy)
                .build();
    }

    private User createChild(Long usersId) {
        return User.builder()
                .usersId(usersId)
                .name("테스트")
                .role(Role.CHILD)
                .build();
    }

    private void setCurrentUser(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null)
        );
    }
}
