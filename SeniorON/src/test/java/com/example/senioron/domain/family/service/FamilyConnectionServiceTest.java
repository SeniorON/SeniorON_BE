package com.example.senioron.domain.family.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.senioron.domain.family.dto.response.FamilyConnectionDisconnectResponse;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyMember;
import com.example.senioron.domain.family.repository.FamilyMemberRepository;
import com.example.senioron.domain.family.repository.FamilyRepository;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.repository.SeniorRepository;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class FamilyConnectionServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final FamilyRepository familyRepository = mock(FamilyRepository.class);
    private final FamilyMemberRepository familyMemberRepository =
            mock(FamilyMemberRepository.class);
    private final SeniorRepository seniorRepository = mock(SeniorRepository.class);
    private final UnusedFamilyCleanupService unusedFamilyCleanupService =
            mock(UnusedFamilyCleanupService.class);
    private final FamilyConnectionService service = new FamilyConnectionService(
            userRepository,
            familyRepository,
            familyMemberRepository,
            seniorRepository,
            unusedFamilyCleanupService
    );

    @Test
    void subChildDisconnectsOnlyOwnMembershipUsingFamilyMemberAuthority() {
        Fixture fixture = stubMembership(Role.CHILD, ManagerType.SUB, null);
        fixture.user().updateManagerType(ManagerType.PRIMARY);

        FamilyConnectionDisconnectResponse response = service.disconnect(
                fixture.user(),
                fixture.family().getFamilyId()
        );

        assertThat(response.familyId()).isEqualTo(fixture.family().getFamilyId());
        assertThat(response.familyDeleted()).isFalse();
        verify(familyMemberRepository).delete(fixture.member());
        InOrder lockOrder = inOrder(
                userRepository,
                familyRepository,
                familyMemberRepository
        );
        lockOrder.verify(familyRepository)
                .findByIdForUpdate(fixture.family().getFamilyId());
        lockOrder.verify(userRepository).findByIdForUpdate(1L);
        lockOrder.verify(familyMemberRepository)
                .findByUserAndFamilyForUpdate(fixture.user(), fixture.family());
        lockOrder.verify(familyMemberRepository).delete(fixture.member());
        verify(familyRepository, never())
                .delete(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(seniorRepository);
        verifyNoInteractions(unusedFamilyCleanupService);
    }

    @Test
    void primaryChildDisconnectsWhenNoBlockingConnectionRemains() {
        Fixture fixture = stubMembership(Role.CHILD, ManagerType.PRIMARY, null);
        fixture.user().updateManagerType(ManagerType.SUB);
        given(familyMemberRepository.findAllByFamilyOrderByIdAsc(fixture.family()))
                .willReturn(List.of(fixture.member()));
        Senior senior = Senior.builder()
                .seniorId(10L)
                .family(fixture.family())
                .build();
        given(seniorRepository.findAllByFamilyOrderBySeniorIdAscForUpdate(
                fixture.family()
        )).willReturn(List.of(senior));

        FamilyConnectionDisconnectResponse response = service.disconnect(
                fixture.user(),
                fixture.family().getFamilyId()
        );

        assertThat(response.familyId()).isEqualTo(fixture.family().getFamilyId());
        assertThat(response.familyDeleted()).isTrue();
        verify(familyMemberRepository).delete(fixture.member());
        verify(familyMemberRepository).flush();
        verify(unusedFamilyCleanupService).cleanup(
                fixture.family(),
                List.of(senior)
        );
    }

    @Test
    void primaryDisconnectIsRejectedWhenSeniorConnectionHistoryExists() {
        Fixture fixture = stubMembership(
                Role.CHILD,
                ManagerType.PRIMARY,
                LocalDateTime.of(2026, 11, 1, 10, 0)
        );

        assertDisconnectError(
                fixture,
                ErrorCode.PRIMARY_CONNECTION_DISCONNECT_HISTORY_EXISTS
        );

        verify(familyMemberRepository, never())
                .findAllByFamilyOrderByIdAsc(fixture.family());
    }

    @Test
    void primaryDisconnectIsRejectedWhenAnotherGuardianRemains() {
        Fixture fixture = stubMembership(Role.CHILD, ManagerType.PRIMARY, null);
        User otherChild = user(2L, Role.CHILD);
        FamilyMember otherMember = member(
                2L,
                otherChild,
                fixture.family(),
                ManagerType.SUB
        );
        given(familyMemberRepository.findAllByFamilyOrderByIdAsc(fixture.family()))
                .willReturn(List.of(fixture.member(), otherMember));

        assertDisconnectError(
                fixture,
                ErrorCode.PRIMARY_CONNECTION_DISCONNECT_GUARDIAN_EXISTS
        );

        verifyNoInteractions(seniorRepository);
        verifyNoInteractions(unusedFamilyCleanupService);
    }

    @Test
    void primaryDisconnectIsRejectedWhenParentMemberRemains() {
        Fixture fixture = stubMembership(Role.CHILD, ManagerType.PRIMARY, null);
        FamilyMember parentMember = member(
                2L,
                user(2L, Role.PARENT),
                fixture.family(),
                ManagerType.NONE
        );
        given(familyMemberRepository.findAllByFamilyOrderByIdAsc(fixture.family()))
                .willReturn(List.of(fixture.member(), parentMember));

        assertDisconnectError(
                fixture,
                ErrorCode.PRIMARY_CONNECTION_DISCONNECT_PARENT_EXISTS
        );

        verifyNoInteractions(seniorRepository);
    }

    @Test
    void primaryDisconnectIsRejectedWhenSeniorHasLinkedParentUser() {
        Fixture fixture = stubMembership(Role.CHILD, ManagerType.PRIMARY, null);
        User parent = user(2L, Role.PARENT);
        Senior senior = Senior.builder()
                .seniorId(10L)
                .family(fixture.family())
                .parentUser(parent)
                .build();
        given(familyMemberRepository.findAllByFamilyOrderByIdAsc(fixture.family()))
                .willReturn(List.of(fixture.member()));
        given(seniorRepository.findAllByFamilyOrderBySeniorIdAscForUpdate(
                fixture.family()
        )).willReturn(List.of(senior));

        assertDisconnectError(
                fixture,
                ErrorCode.PRIMARY_CONNECTION_DISCONNECT_PARENT_EXISTS
        );
    }

    @Test
    void parentCannotUseFamilyConnectionDisconnectApi() {
        Fixture fixture = stubMembership(Role.PARENT, ManagerType.NONE, null);

        assertDisconnectError(
                fixture,
                ErrorCode.FAMILY_CONNECTION_DISCONNECT_FORBIDDEN
        );

        verify(familyMemberRepository, never())
                .findAllByFamilyOrderByIdAsc(fixture.family());
        verifyNoInteractions(seniorRepository);
    }

    @Test
    void childWithNoneManagerTypeCannotDisconnect() {
        Fixture fixture = stubMembership(Role.CHILD, ManagerType.NONE, null);

        assertDisconnectError(
                fixture,
                ErrorCode.FAMILY_CONNECTION_DISCONNECT_FORBIDDEN
        );
    }

    @Test
    void childWithoutFamilyManagerTypeCannotDisconnect() {
        Fixture fixture = stubMembership(Role.CHILD, null, null);

        assertDisconnectError(
                fixture,
                ErrorCode.FAMILY_CONNECTION_DISCONNECT_FORBIDDEN
        );
    }

    @Test
    void nonMemberReceivesFamilyMemberNotFound() {
        Family family = family(null);
        User child = user(1L, Role.CHILD);
        given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(child));
        given(familyRepository.findByIdForUpdate(family.getFamilyId()))
                .willReturn(Optional.of(family));
        given(familyMemberRepository.findByUserAndFamilyForUpdate(child, family))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.disconnect(child, family.getFamilyId()))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.FAMILY_MEMBER_NOT_FOUND);

        verify(familyMemberRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void missingFamilyIsRejectedBeforeMembershipLookup() {
        User child = user(1L, Role.CHILD);
        given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(child));
        given(familyRepository.findByIdForUpdate(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.disconnect(child, 99L))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.FAMILY_NOT_FOUND);

        verifyNoInteractions(familyMemberRepository, seniorRepository);
        verifyNoInteractions(unusedFamilyCleanupService);
    }

    private Fixture stubMembership(
            Role role,
            ManagerType managerType,
            LocalDateTime firstSeniorConnectedAt
    ) {
        Family family = family(firstSeniorConnectedAt);
        User user = user(1L, role);
        FamilyMember member = member(1L, user, family, managerType);

        given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
        given(familyRepository.findByIdForUpdate(family.getFamilyId()))
                .willReturn(Optional.of(family));
        given(familyMemberRepository.findByUserAndFamilyForUpdate(user, family))
                .willReturn(Optional.of(member));
        return new Fixture(family, user, member);
    }

    private void assertDisconnectError(Fixture fixture, ErrorCode errorCode) {
        assertThatThrownBy(() -> service.disconnect(
                fixture.user(),
                fixture.family().getFamilyId()
        ))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(errorCode);

        verify(familyMemberRepository, never()).delete(fixture.member());
    }

    private Family family(LocalDateTime firstSeniorConnectedAt) {
        return Family.builder()
                .familyId(10L)
                .seniorCode("ABCD-1234")
                .firstSeniorConnectedAt(firstSeniorConnectedAt)
                .build();
    }

    private User user(Long userId, Role role) {
        return User.builder()
                .usersId(userId)
                .name("사용자-" + userId)
                .role(role)
                .build();
    }

    private FamilyMember member(
            Long memberId,
            User user,
            Family family,
            ManagerType managerType
    ) {
        return FamilyMember.builder()
                .id(memberId)
                .user(user)
                .family(family)
                .managerType(managerType)
                .build();
    }

    private record Fixture(
            Family family,
            User user,
            FamilyMember member
    ) {
    }
}
