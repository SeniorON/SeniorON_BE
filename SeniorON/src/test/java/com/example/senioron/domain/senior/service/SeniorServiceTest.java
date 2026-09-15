package com.example.senioron.domain.senior.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyMember;
import com.example.senioron.domain.family.repository.FamilyMemberRepository;
import com.example.senioron.domain.family.repository.FamilyRepository;
import com.example.senioron.domain.senior.dto.request.SeniorCreateRequest;
import com.example.senioron.domain.senior.dto.request.SeniorRelationUpdateRequest;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.entity.SeniorRelation;
import com.example.senioron.domain.senior.repository.SeniorRepository;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class SeniorServiceTest {

    private final SeniorRepository seniorRepository =
            org.mockito.Mockito.mock(SeniorRepository.class);
    private final UserRepository userRepository =
            org.mockito.Mockito.mock(UserRepository.class);
    private final FamilyRepository familyRepository =
            org.mockito.Mockito.mock(FamilyRepository.class);
    private final FamilyMemberRepository familyMemberRepository =
            org.mockito.Mockito.mock(FamilyMemberRepository.class);

    private SeniorService seniorService;

    @BeforeEach
    void setUp() {
        seniorService = new SeniorService(
                seniorRepository,
                userRepository,
                familyRepository,
                familyMemberRepository
        );
    }

    @Test
    void getManagedSeniorsReturnsSeniorsThroughFamilyMembers() {
        Family family = Family.builder().familyId(1L).build();
        User user = createChild(1L, family, ManagerType.PRIMARY);
        addFamilyMember(user, family, ManagerType.PRIMARY);
        Senior senior = createSenior(10L, family, user);

        given(userRepository.findByIdWithFamily(1L)).willReturn(Optional.of(user));
        given(seniorRepository.findFirstByFamilyOrderBySeniorIdAsc(family))
                .willReturn(Optional.of(senior));

        var response = seniorService.getManagedSeniors(user);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).seniorId()).isEqualTo(10L);
        assertThat(response.get(0).familyId()).isEqualTo(1L);
        assertThat(response.get(0).parentUserId()).isNull();
        assertThat(response.get(0).name()).isEqualTo("김영희");
        assertThat(response.get(0).relation()).isEqualTo(SeniorRelation.MOTHER);
        assertThat(response.get(0).customRelation()).isNull();
    }

    @Test
    void getManagedSeniorsRejectsUnauthenticatedUser() {
        assertThatThrownBy(() -> seniorService.getManagedSeniors(null))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.USER_NOT_AUTHENTICATED);
    }

    @Test
    void getFamilySeniorsReturnsFamilySenior() {
        Family family = Family.builder().familyId(1L).build();
        User user = createChild(1L, family, ManagerType.PRIMARY);
        Senior senior = createSenior(10L, family, user);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(familyRepository.findById(1L)).willReturn(Optional.of(family));
        given(familyMemberRepository.existsByUserAndFamily(user, family)).willReturn(true);
        given(seniorRepository.findAllByFamilyOrderBySeniorIdAsc(family))
                .willReturn(java.util.List.of(senior));

        var response = seniorService.getFamilySeniors(user, 1L);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).seniorId()).isEqualTo(10L);
    }

    @Test
    void getFamilySeniorsRejectsUserOutsideRequestedFamily() {
        Family family = Family.builder().familyId(1L).build();
        User user = createChild(1L, null, ManagerType.PRIMARY);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(familyRepository.findById(1L)).willReturn(Optional.of(family));
        given(familyMemberRepository.existsByUserAndFamily(user, family)).willReturn(false);

        assertThatThrownBy(() -> seniorService.getFamilySeniors(user, 1L))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void createSeniorCreatesFamilySenior() {
        Family family = Family.builder().familyId(1L).build();
        User user = createChild(1L, family, ManagerType.PRIMARY);
        Senior savedSenior = createSenior(10L, family, user);

        given(familyRepository.findByIdForUpdate(1L)).willReturn(Optional.of(family));
        given(familyMemberRepository.existsByUserAndFamily(user, family)).willReturn(true);
        given(seniorRepository.saveAndFlush(any(Senior.class))).willReturn(savedSenior);

        var response = seniorService.createSenior(
                user,
                createRequest(SeniorRelation.MOTHER, null)
        );

        assertThat(response.seniorId()).isEqualTo(10L);
        assertThat(response.relation()).isEqualTo(SeniorRelation.MOTHER);
        verify(seniorRepository).saveAndFlush(any(Senior.class));
    }

    @Test
    void createSeniorConvertsUniqueViolationToSeniorAlreadyExists() {
        Family family = Family.builder().familyId(1L).build();
        User user = createChild(1L, family, ManagerType.PRIMARY);

        given(familyRepository.findByIdForUpdate(1L)).willReturn(Optional.of(family));
        given(familyMemberRepository.existsByUserAndFamily(user, family)).willReturn(true);
        given(seniorRepository.saveAndFlush(any(Senior.class)))
                .willThrow(new DataIntegrityViolationException("duplicate family senior"));

        assertThatThrownBy(() -> seniorService.createSenior(
                user,
                createRequest(SeniorRelation.MOTHER, null)
        ))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.SENIOR_ALREADY_EXISTS);
    }

    @Test
    void updateSeniorRelationValidatesSameFamilyAndReturnsRequestedRelation() {
        Family family = Family.builder().familyId(1L).build();
        User user = createChild(2L, family, ManagerType.SUB);
        Senior senior = createSenior(10L, family, user);

        given(userRepository.findByIdForUpdate(2L)).willReturn(Optional.of(user));
        given(seniorRepository.findById(10L)).willReturn(Optional.of(senior));
        given(familyMemberRepository.existsByUserAndFamily(user, family)).willReturn(true);

        var response = seniorService.updateSeniorRelation(
                user,
                10L,
                new SeniorRelationUpdateRequest(SeniorRelation.GRANDPARENT, null)
        );

        assertThat(response.seniorId()).isEqualTo(10L);
        assertThat(response.relation()).isEqualTo(SeniorRelation.GRANDPARENT);
        assertThat(senior.getRelation()).isEqualTo(SeniorRelation.GRANDPARENT);
    }

    private SeniorCreateRequest createRequest(
            SeniorRelation relation,
            String customRelation
    ) {
        return new SeniorCreateRequest(
                1L,
                "김영희",
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

    private Senior createSenior(
            Long seniorId,
            Family family,
            User registeredBy
    ) {
        return Senior.builder()
                .seniorId(seniorId)
                .name("김영희")
                .relation(SeniorRelation.MOTHER)
                .birth(LocalDate.of(1950, 1, 1))
                .phoneNumber("01012345678")
                .address("서울시")
                .detailAddress("101호")
                .latitude(37.5665)
                .longitude(126.9780)
                .family(family)
                .registeredBy(registeredBy)
                .build();
    }

    private User createChild(
            Long usersId,
            Family family,
            ManagerType managerType
    ) {
        return User.builder()
                .usersId(usersId)
                .name("자녀")
                .role(Role.CHILD)
                .managerType(managerType)
                .family(family)
                .build();
    }

    private User createParent(Long usersId, Family family) {
        return User.builder()
                .usersId(usersId)
                .name("부모")
                .role(Role.PARENT)
                .managerType(ManagerType.NONE)
                .family(family)
                .build();
    }

    private void addFamilyMember(
            User user,
            Family family,
            ManagerType managerType
    ) {
        user.getFamilyMembers().add(
                FamilyMember.builder()
                        .user(user)
                        .family(family)
                        .managerType(managerType)
                        .build()
        );
    }
}
