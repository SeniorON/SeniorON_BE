package com.example.senioron.domain.family.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.senioron.domain.event.repository.EventRepository;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.repository.FamilyMemberRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoSeniorRepository;
import com.example.senioron.domain.family.repository.FamilyRepository;
import com.example.senioron.domain.permission.repository.SeniorPermissionSettingRepository;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.repository.SeniorReloginRequestRepository;
import com.example.senioron.domain.senior.repository.SeniorRepository;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;

class UnusedFamilyCleanupServiceTest {

    private final FamilyRepository familyRepository = mock(FamilyRepository.class);
    private final FamilyMemberRepository familyMemberRepository =
            mock(FamilyMemberRepository.class);
    private final SeniorRepository seniorRepository = mock(SeniorRepository.class);
    private final SeniorPermissionSettingRepository permissionSettingRepository =
            mock(SeniorPermissionSettingRepository.class);
    private final SeniorReloginRequestRepository reloginRequestRepository =
            mock(SeniorReloginRequestRepository.class);
    private final EventRepository eventRepository = mock(EventRepository.class);
    private final FamilyPhotoSeniorRepository familyPhotoSeniorRepository =
            mock(FamilyPhotoSeniorRepository.class);
    private final UnusedFamilyCleanupService service =
            new UnusedFamilyCleanupService(
                    familyRepository,
                    familyMemberRepository,
                    seniorRepository,
                    permissionSettingRepository,
                    reloginRequestRepository,
                    eventRepository,
                    familyPhotoSeniorRepository
            );

    @Test
    void deletesOnlyDisposableSeniorDataBeforeFamily() {
        Family family = family(null);
        Senior senior = senior(family, null);

        service.cleanup(family, List.of(senior));

        InOrder order = inOrder(
                permissionSettingRepository,
                seniorRepository,
                familyRepository
        );
        order.verify(permissionSettingRepository).deleteAllBySenior(senior);
        order.verify(permissionSettingRepository).flush();
        order.verify(seniorRepository).deleteAll(List.of(senior));
        order.verify(seniorRepository).flush();
        order.verify(familyRepository).delete(family);
        order.verify(familyRepository).flush();
    }

    @Test
    void historyBlocksCleanup() {
        Family family = family(LocalDateTime.of(2026, 11, 1, 10, 0));

        assertCleanupBlocked(family, List.of());

        verifyNoInteractions(
                permissionSettingRepository,
                seniorRepository,
                reloginRequestRepository,
                eventRepository,
                familyPhotoSeniorRepository
        );
    }

    @Test
    void remainingMembershipBlocksCleanup() {
        Family family = family(null);
        given(familyMemberRepository.existsByFamily(family)).willReturn(true);

        assertCleanupBlocked(family, List.of());

        verify(familyRepository, never()).delete(family);
    }

    @Test
    void linkedParentBlocksCleanup() {
        Family family = family(null);
        Senior senior = senior(family, user(2L, Role.PARENT));

        assertCleanupBlocked(family, List.of(senior));

        verifyNoInteractions(
                reloginRequestRepository,
                eventRepository,
                familyPhotoSeniorRepository
        );
    }

    @Test
    void reloginRequestBlocksCleanup() {
        Family family = family(null);
        Senior senior = senior(family, null);
        given(reloginRequestRepository.existsBySenior(senior)).willReturn(true);

        assertCleanupBlocked(family, List.of(senior));

        verify(eventRepository, never()).existsBySenior(senior);
        verify(familyPhotoSeniorRepository, never()).existsBySenior(senior);
    }

    @Test
    void eventBlocksCleanup() {
        Family family = family(null);
        Senior senior = senior(family, null);
        given(eventRepository.existsBySenior(senior)).willReturn(true);

        assertCleanupBlocked(family, List.of(senior));

        verify(familyPhotoSeniorRepository, never()).existsBySenior(senior);
    }

    @Test
    void sharedPhotoBlocksCleanup() {
        Family family = family(null);
        Senior senior = senior(family, null);
        given(familyPhotoSeniorRepository.existsBySenior(senior))
                .willReturn(true);

        assertCleanupBlocked(family, List.of(senior));
    }

    @Test
    void foreignKeyFailureIsConvertedToCleanupBlockedError() {
        Family family = family(null);
        Senior senior = senior(family, null);
        doThrow(new DataIntegrityViolationException("unexpected FK"))
                .when(seniorRepository).flush();

        assertCleanupBlocked(family, List.of(senior));

        verify(familyRepository, never()).delete(family);
    }

    private void assertCleanupBlocked(Family family, List<Senior> seniors) {
        assertThatThrownBy(() -> service.cleanup(family, seniors))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.UNUSED_FAMILY_CLEANUP_BLOCKED);

        verify(familyRepository, never()).delete(family);
    }

    private Family family(LocalDateTime firstSeniorConnectedAt) {
        return Family.builder()
                .familyId(10L)
                .seniorCode("ABCD-1234")
                .firstSeniorConnectedAt(firstSeniorConnectedAt)
                .build();
    }

    private Senior senior(Family family, User parentUser) {
        return Senior.builder()
                .seniorId(20L)
                .family(family)
                .parentUser(parentUser)
                .build();
    }

    private User user(Long userId, Role role) {
        return User.builder()
                .usersId(userId)
                .name("사용자-" + userId)
                .role(role)
                .build();
    }
}
