package com.example.senioron.domain.senior.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyMember;
import com.example.senioron.domain.family.repository.FamilyMemberRepository;
import com.example.senioron.domain.family.repository.FamilyRepository;
import com.example.senioron.domain.senior.dto.request.SeniorCreateRequest;
import com.example.senioron.domain.senior.dto.response.ManagedSeniorResponse;
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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(familyMemberRepository.findManagedSeniorResponsesByUserId(1L))
                .willReturn(List.of(new ManagedSeniorResponse(1L, 10L)));

        var response = seniorService.getManagedSeniors(user);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).familyId()).isEqualTo(1L);
        assertThat(response.get(0).seniorId()).isEqualTo(10L);
        verify(familyMemberRepository).findManagedSeniorResponsesByUserId(1L);
        verify(seniorRepository, never()).findFirstByFamilyOrderBySeniorIdAsc(any());
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
    void getManagedSeniorsReturnsEmptyWhenUserHasNoFamily() {
        User user = createChild(1L, null, ManagerType.NONE);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(familyMemberRepository.findManagedSeniorResponsesByUserId(1L))
                .willReturn(List.of());

        var response = seniorService.getManagedSeniors(user);

        assertThat(response).isEmpty();
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
    void getParentSeniorProfileReturnsLinkedSeniorId() {
        User parent = createParent(1L, null);
        Senior senior = Senior.builder()
                .seniorId(10L)
                .parentUser(parent)
                .build();

        given(userRepository.findById(1L)).willReturn(Optional.of(parent));
        given(seniorRepository.findByParentUser(parent)).willReturn(Optional.of(senior));

        var response = seniorService.getParentSeniorProfile(parent);

        assertThat(response.seniorId()).isEqualTo(10L);
        verify(seniorRepository).findByParentUser(parent);
    }

    @Test
    void getParentSeniorProfileUsesParentUserInsteadOfFamilyMembership() {
        Family firstFamily = Family.builder().familyId(1L).build();
        Family linkedFamily = Family.builder().familyId(2L).build();
        User parent = createParent(1L, firstFamily);
        addFamilyMember(parent, firstFamily, ManagerType.NONE);
        addFamilyMember(parent, linkedFamily, ManagerType.NONE);
        Senior linkedSenior = Senior.builder()
                .seniorId(20L)
                .family(linkedFamily)
                .parentUser(parent)
                .build();

        given(userRepository.findById(1L)).willReturn(Optional.of(parent));
        given(seniorRepository.findByParentUser(parent))
                .willReturn(Optional.of(linkedSenior));

        var response = seniorService.getParentSeniorProfile(parent);

        assertThat(response.seniorId()).isEqualTo(20L);
        verifyNoInteractions(familyMemberRepository);
    }

    @Test
    void getParentSeniorProfileRejectsParentWithoutLinkedSenior() {
        User parent = createParent(1L, null);

        given(userRepository.findById(1L)).willReturn(Optional.of(parent));
        given(seniorRepository.findByParentUser(parent)).willReturn(Optional.empty());

        assertThatThrownBy(() -> seniorService.getParentSeniorProfile(parent))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.SENIOR_NOT_FOUND);
    }

    @Test
    void getParentSeniorProfileRejectsChildUser() {
        User child = createChild(1L, null, ManagerType.PRIMARY);

        given(userRepository.findById(1L)).willReturn(Optional.of(child));

        assertThatThrownBy(() -> seniorService.getParentSeniorProfile(child))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.SENIOR_PARENT_LINK_PARENT_ONLY);
        verify(seniorRepository, never()).findByParentUser(child);
    }

    @Test
    void getParentSeniorProfileReturnsOnlyCurrentParentsSenior() {
        User currentParent = createParent(1L, null);
        User otherParent = createParent(2L, null);
        Senior otherSenior = Senior.builder()
                .seniorId(30L)
                .parentUser(otherParent)
                .build();
        Senior currentSenior = Senior.builder()
                .seniorId(40L)
                .parentUser(currentParent)
                .build();

        given(userRepository.findById(1L)).willReturn(Optional.of(currentParent));
        given(seniorRepository.findByParentUser(currentParent))
                .willReturn(Optional.of(currentSenior));

        var response = seniorService.getParentSeniorProfile(currentParent);

        assertThat(response.seniorId()).isEqualTo(40L);
        assertThat(response.seniorId()).isNotEqualTo(otherSenior.getSeniorId());
        verify(seniorRepository).findByParentUser(currentParent);
        verify(seniorRepository, never()).findByParentUser(otherParent);
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
    void createSeniorLinksExistingParentMember() {
        Family family = Family.builder().familyId(1L).build();
        User child = createChild(1L, family, ManagerType.PRIMARY);
        User parent = createParent(2L, family);

        given(familyRepository.findByIdForUpdate(1L)).willReturn(Optional.of(family));
        given(familyMemberRepository.existsByUserAndFamily(child, family)).willReturn(true);
        given(familyMemberRepository.findByFamilyAndUserIdNotAndUserRole(
                family,
                1L,
                Role.PARENT
        )).willReturn(java.util.List.of(
                FamilyMember.builder()
                        .user(parent)
                        .family(family)
                        .managerType(ManagerType.NONE)
                        .build()
        ));
        given(seniorRepository.existsByParentUser(parent)).willReturn(false);
        given(seniorRepository.saveAndFlush(any(Senior.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        seniorService.createSenior(
                child,
                createRequest(SeniorRelation.MOTHER, null)
        );

        ArgumentCaptor<Senior> seniorCaptor = ArgumentCaptor.forClass(Senior.class);
        verify(seniorRepository).saveAndFlush(seniorCaptor.capture());
        assertThat(seniorCaptor.getValue().getParentUser()).isEqualTo(parent);
    }

    @Test
    void createSeniorRejectsParentUser() {
        Family family = Family.builder().familyId(1L).build();
        User parent = createParent(2L, family);

        assertThatThrownBy(() -> seniorService.createSenior(
                parent,
                createRequest(SeniorRelation.MOTHER, null)
        ))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.SENIOR_CREATE_CHILD_ONLY);
        verify(familyRepository, never()).findByIdForUpdate(1L);
        verify(seniorRepository, never()).saveAndFlush(any(Senior.class));
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
