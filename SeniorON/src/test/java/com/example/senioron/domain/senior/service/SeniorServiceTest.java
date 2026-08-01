package com.example.senioron.domain.senior.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.repository.FamilyRepository;
import com.example.senioron.domain.senior.dto.request.SeniorCreateRequest;
import com.example.senioron.domain.senior.dto.request.SeniorRelationUpdateRequest;
import com.example.senioron.domain.senior.dto.response.SeniorCreateResponse;
import com.example.senioron.domain.senior.dto.response.SeniorRelationUpdateResponse;
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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class SeniorServiceTest {

    private final SeniorRepository seniorRepository = org.mockito.Mockito.mock(SeniorRepository.class);
    private final UserSeniorRepository userSeniorRepository = org.mockito.Mockito.mock(UserSeniorRepository.class);
    private final UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
    private final FamilyRepository familyRepository = org.mockito.Mockito.mock(FamilyRepository.class);

    private SeniorService seniorService;

    @BeforeEach
    void setUp() {
        seniorService = new SeniorService(seniorRepository, userSeniorRepository, userRepository, familyRepository);
    }

    @Test
    void createSeniorCreatesSeniorAndUserSeniorTogether() {
        Family family = Family.builder().familyId(1L).build();
        User user = createChild(1L, family, ManagerType.PRIMARY);
        Senior savedSenior = createSenior(10L, family, user);
        UserSenior savedUserSenior = UserSenior.builder()
                .userSeniorId(20L)
                .user(user)
                .senior(savedSenior)
                .relation(SeniorRelation.MOTHER)
                .build();
        given(familyRepository.findByIdForUpdate(1L)).willReturn(Optional.of(family));
        given(seniorRepository.findFirstByFamily(family)).willReturn(Optional.empty());
        given(seniorRepository.saveAndFlush(any(Senior.class))).willReturn(savedSenior);
        given(userSeniorRepository.save(any(UserSenior.class))).willReturn(savedUserSenior);

        SeniorCreateResponse response = seniorService.createSenior(user, createRequest(SeniorRelation.MOTHER, null));

        assertThat(response.seniorId()).isEqualTo(10L);
        assertThat(response.relation()).isEqualTo(SeniorRelation.MOTHER);
        assertThat(response.latitude()).isEqualTo(37.5665);
        assertThat(response.longitude()).isEqualTo(126.9780);
        verify(seniorRepository).saveAndFlush(any(Senior.class));
        verify(userSeniorRepository).save(any(UserSenior.class));
    }

    @Test
    void createSeniorThrowsExceptionWhenFamilyAlreadyHasSenior() {
        Family family = Family.builder().familyId(1L).build();
        User user = createChild(1L, family, ManagerType.PRIMARY);
        given(familyRepository.findByIdForUpdate(1L)).willReturn(Optional.of(family));
        given(seniorRepository.findFirstByFamily(family)).willReturn(Optional.of(createSenior(10L, family, user)));

        assertThatThrownBy(() -> seniorService.createSenior(user, createRequest(SeniorRelation.MOTHER, null)))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.SENIOR_ALREADY_EXISTS);
    }

    @Test
    void createSeniorThrowsExceptionWhenUserHasNoFamily() {
        User user = createChild(1L, null, ManagerType.PRIMARY);

        assertThatThrownBy(() -> seniorService.createSenior(user, createRequest(SeniorRelation.MOTHER, null)))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.FAMILY_NOT_CONNECTED);
    }

    @Test
    void createSeniorConvertsUniqueViolationToSeniorAlreadyExists() {
        Family family = Family.builder().familyId(1L).build();
        User user = createChild(1L, family, ManagerType.PRIMARY);
        given(familyRepository.findByIdForUpdate(1L)).willReturn(Optional.of(family));
        given(seniorRepository.findFirstByFamily(family)).willReturn(Optional.empty());
        given(seniorRepository.saveAndFlush(any(Senior.class)))
                .willThrow(new DataIntegrityViolationException("duplicate family senior"));

        assertThatThrownBy(() -> seniorService.createSenior(user, createRequest(SeniorRelation.MOTHER, null)))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.SENIOR_ALREADY_EXISTS);
    }

    @Test
    void updateSeniorRelationCreatesUserSeniorWhenMissing() {
        Family family = Family.builder().familyId(1L).build();
        User subChild = createChild(2L, family, ManagerType.SUB);
        Senior senior = createSenior(10L, family, subChild);
        given(userRepository.findByIdForUpdate(2L)).willReturn(Optional.of(subChild));
        given(seniorRepository.findById(10L)).willReturn(Optional.of(senior));
        given(userSeniorRepository.findByUserAndSenior(subChild, senior)).willReturn(Optional.empty());
        given(userSeniorRepository.save(any(UserSenior.class))).willAnswer(invocation -> invocation.getArgument(0));

        SeniorRelationUpdateResponse response = seniorService.updateSeniorRelation(
                subChild,
                10L,
                new SeniorRelationUpdateRequest(SeniorRelation.GRANDPARENT, null)
        );

        assertThat(response.seniorId()).isEqualTo(10L);
        assertThat(response.relation()).isEqualTo(SeniorRelation.GRANDPARENT);
    }

    @Test
    void updateSeniorRelationUpdatesOnlyCurrentUsersRelation() {
        Family family = Family.builder().familyId(1L).build();
        User primaryChild = createChild(1L, family, ManagerType.PRIMARY);
        User subChild = createChild(2L, family, ManagerType.SUB);
        Senior senior = createSenior(10L, family, primaryChild);
        UserSenior primaryRelation = UserSenior.builder()
                .user(primaryChild)
                .senior(senior)
                .relation(SeniorRelation.MOTHER)
                .build();
        UserSenior subRelation = UserSenior.builder()
                .user(subChild)
                .senior(senior)
                .relation(SeniorRelation.GRANDPARENT)
                .build();
        given(userRepository.findByIdForUpdate(2L)).willReturn(Optional.of(subChild));
        given(seniorRepository.findById(10L)).willReturn(Optional.of(senior));
        given(userSeniorRepository.findByUserAndSenior(subChild, senior)).willReturn(Optional.of(subRelation));
        given(userSeniorRepository.save(any(UserSenior.class))).willAnswer(invocation -> invocation.getArgument(0));

        seniorService.updateSeniorRelation(
                subChild,
                10L,
                new SeniorRelationUpdateRequest(SeniorRelation.OTHER, "외할머니")
        );

        assertThat(subRelation.getRelation()).isEqualTo(SeniorRelation.OTHER);
        assertThat(subRelation.getCustomRelation()).isEqualTo("외할머니");
        assertThat(primaryRelation.getRelation()).isEqualTo(SeniorRelation.MOTHER);
    }

    @Test
    void updateSeniorRelationRejectsSeniorFromDifferentFamily() {
        Family family = Family.builder().familyId(1L).build();
        Family otherFamily = Family.builder().familyId(2L).build();
        User subChild = createChild(2L, family, ManagerType.SUB);
        Senior senior = createSenior(10L, otherFamily, subChild);
        given(userRepository.findByIdForUpdate(2L)).willReturn(Optional.of(subChild));
        given(seniorRepository.findById(10L)).willReturn(Optional.of(senior));

        assertThatThrownBy(() -> seniorService.updateSeniorRelation(
                subChild,
                10L,
                new SeniorRelationUpdateRequest(SeniorRelation.GRANDPARENT, null)
        ))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void updateSeniorRelationRequiresCustomRelationWhenOther() {
        Family family = Family.builder().familyId(1L).build();
        User subChild = createChild(2L, family, ManagerType.SUB);
        given(userRepository.findByIdForUpdate(2L)).willReturn(Optional.of(subChild));

        assertThatThrownBy(() -> seniorService.updateSeniorRelation(
                subChild,
                10L,
                new SeniorRelationUpdateRequest(SeniorRelation.OTHER, " ")
        ))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.CUSTOM_RELATION_REQUIRED);
    }

    @Test
    void updateSeniorRelationUsesLockedUserForFamilyValidationAndUpsert() {
        Family staleFamily = Family.builder().familyId(1L).build();
        Family currentFamily = Family.builder().familyId(2L).build();
        User stalePrincipal = createChild(2L, staleFamily, ManagerType.SUB);
        User lockedUser = createChild(2L, currentFamily, ManagerType.SUB);
        Senior senior = createSenior(10L, currentFamily, lockedUser);
        given(userRepository.findByIdForUpdate(2L)).willReturn(Optional.of(lockedUser));
        given(seniorRepository.findById(10L)).willReturn(Optional.of(senior));
        given(userSeniorRepository.findByUserAndSenior(lockedUser, senior)).willReturn(Optional.empty());
        given(userSeniorRepository.save(any(UserSenior.class))).willAnswer(invocation -> invocation.getArgument(0));

        seniorService.updateSeniorRelation(
                stalePrincipal,
                10L,
                new SeniorRelationUpdateRequest(SeniorRelation.GRANDPARENT, null)
        );

        verify(userRepository).findByIdForUpdate(2L);
        verify(userSeniorRepository).findByUserAndSenior(lockedUser, senior);
    }

    private SeniorCreateRequest createRequest(SeniorRelation relation, String customRelation) {
        return new SeniorCreateRequest(
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

    private Senior createSenior(Long seniorId, Family family, User registeredBy) {
        return Senior.builder()
                .seniorId(seniorId)
                .name("김영희")
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

    private User createChild(Long usersId, Family family, ManagerType managerType) {
        return User.builder()
                .usersId(usersId)
                .name("자녀")
                .role(Role.CHILD)
                .managerType(managerType)
                .family(family)
                .build();
    }
}
