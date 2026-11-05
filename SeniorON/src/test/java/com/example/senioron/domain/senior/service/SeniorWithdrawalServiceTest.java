package com.example.senioron.domain.senior.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.senioron.domain.device.entity.Device;
import com.example.senioron.domain.device.repository.DeviceRepository;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyMember;
import com.example.senioron.domain.family.repository.FamilyMemberRepository;
import com.example.senioron.domain.family.repository.FamilyRepository;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.entity.SeniorRelation;
import com.example.senioron.domain.senior.entity.SeniorWithdrawalNotice;
import com.example.senioron.domain.senior.entity.SeniorWithdrawalNoticeRecipient;
import com.example.senioron.domain.senior.event.SeniorWithdrawalNoticeCreatedEvent;
import com.example.senioron.domain.senior.repository.SeniorRepository;
import com.example.senioron.domain.senior.repository.SeniorWithdrawalDataCleanupRepository;
import com.example.senioron.domain.senior.repository.SeniorWithdrawalNoticeRecipientRepository;
import com.example.senioron.domain.senior.repository.SeniorWithdrawalNoticeRepository;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.entity.UserStatus;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class SeniorWithdrawalServiceTest {

    private final SeniorRepository seniorRepository = org.mockito.Mockito.mock(SeniorRepository.class);
    private final FamilyRepository familyRepository = org.mockito.Mockito.mock(FamilyRepository.class);
    private final FamilyMemberRepository familyMemberRepository = org.mockito.Mockito.mock(FamilyMemberRepository.class);
    private final DeviceRepository deviceRepository = org.mockito.Mockito.mock(DeviceRepository.class);
    private final SeniorWithdrawalNoticeRepository noticeRepository =
            org.mockito.Mockito.mock(SeniorWithdrawalNoticeRepository.class);
    private final SeniorWithdrawalNoticeRecipientRepository recipientRepository =
            org.mockito.Mockito.mock(SeniorWithdrawalNoticeRecipientRepository.class);
    private final SeniorWithdrawalDataCleanupRepository cleanupRepository =
            org.mockito.Mockito.mock(SeniorWithdrawalDataCleanupRepository.class);
    private final ApplicationEventPublisher eventPublisher =
            org.mockito.Mockito.mock(ApplicationEventPublisher.class);

    private SeniorWithdrawalService service;

    @BeforeEach
    void setUp() {
        service = new SeniorWithdrawalService(
                seniorRepository,
                familyRepository,
                familyMemberRepository,
                deviceRepository,
                noticeRepository,
                recipientRepository,
                cleanupRepository,
                eventPublisher
        );
        ReflectionTestUtils.setField(service, "retentionDays", 30L);
    }

    @Test
    void createsOneRecipientPerActivePrimaryOrSubGuardianAndDeletesSenior() {
        Family family = Family.builder().familyId(1L).seniorCode("ABCD-EFGH").build();
        User parent = user(10L, Role.PARENT, UserStatus.ACTIVE);
        User primary = user(20L, Role.CHILD, UserStatus.ACTIVE);
        User sub = user(21L, Role.CHILD, UserStatus.ACTIVE);
        User withdrawnChild = user(22L, Role.CHILD, UserStatus.WITHDRAWN);
        Senior senior = senior(100L, family, primary, parent);

        FamilyMember primaryMember = member(1L, primary, family, ManagerType.PRIMARY, SeniorRelation.MOTHER, null);
        FamilyMember subMember = member(2L, sub, family, ManagerType.SUB, SeniorRelation.OTHER, "이모");
        FamilyMember withdrawnMember = member(
                3L, withdrawnChild, family, ManagerType.SUB, SeniorRelation.FATHER, null);
        FamilyMember parentMember = member(4L, parent, family, ManagerType.NONE, null, null);

        Device primaryDevice = Device.builder()
                .user(primary).deviceIdentifier("primary-device").deviceToken("primary-token").build();
        Device duplicatePrimaryToken = Device.builder()
                .user(primary).deviceIdentifier("primary-device-2").deviceToken("primary-token").build();
        Device subWithoutToken = Device.builder()
                .user(sub).deviceIdentifier("sub-device").deviceToken(null).build();

        given(seniorRepository.findByParentUserForUpdate(parent)).willReturn(Optional.of(senior));
        given(familyRepository.findByIdForUpdate(1L)).willReturn(Optional.of(family));
        given(familyMemberRepository.findAllByFamilyOrderByIdAsc(family)).willReturn(List.of(
                primaryMember, subMember, withdrawnMember, parentMember));
        given(noticeRepository.findBySeniorId(100L)).willReturn(Optional.empty());
        given(noticeRepository.saveAndFlush(any(SeniorWithdrawalNotice.class)))
                .willAnswer(invocation -> {
                    SeniorWithdrawalNotice notice = invocation.getArgument(0);
                    ReflectionTestUtils.setField(notice, "seniorWithdrawalNoticeId", 500L);
                    return notice;
                });
        given(deviceRepository.findAllByUserIn(List.of(primary, sub)))
                .willReturn(List.of(primaryDevice, duplicatePrimaryToken, subWithoutToken));
        service.withdrawParent(parent);

        ArgumentCaptor<SeniorWithdrawalNotice> noticeCaptor =
                ArgumentCaptor.forClass(SeniorWithdrawalNotice.class);
        verify(noticeRepository).saveAndFlush(noticeCaptor.capture());
        SeniorWithdrawalNotice notice = noticeCaptor.getValue();
        assertThat(notice.getSeniorId()).isEqualTo(100L);
        assertThat(notice.getFamilyId()).isEqualTo(1L);
        assertThat(notice.getSeniorName()).isEqualTo("김영희");
        assertThat(ChronoUnit.DAYS.between(notice.getWithdrawnAt(), notice.getExpiresAt())).isEqualTo(30);
        assertThat(notice.getCompletedAt()).isNull();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<SeniorWithdrawalNoticeRecipient>> recipientsCaptor =
                ArgumentCaptor.forClass(Iterable.class);
        verify(recipientRepository).saveAllAndFlush(recipientsCaptor.capture());
        List<SeniorWithdrawalNoticeRecipient> recipients = new java.util.ArrayList<>();
        recipientsCaptor.getValue().forEach(recipients::add);
        assertThat(recipients).hasSize(2);
        assertThat(recipients)
                .extracting(recipient -> recipient.getRecipientUser().getUsersId())
                .containsExactly(20L, 21L);
        assertThat(recipients).allMatch(recipient -> recipient.getAcknowledgedAt() == null);
        assertThat(recipients.get(1).getRelation()).isEqualTo(SeniorRelation.OTHER);
        assertThat(recipients.get(1).getCustomRelation()).isEqualTo("이모");

        ArgumentCaptor<SeniorWithdrawalNoticeCreatedEvent> eventCaptor =
                ArgumentCaptor.forClass(SeniorWithdrawalNoticeCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().withdrawalNoticeId()).isEqualTo(500L);
        assertThat(eventCaptor.getValue().targets()).hasSize(2);
        assertThat(eventCaptor.getValue().targets().get(0).tokens()).containsExactly("primary-token");
        assertThat(eventCaptor.getValue().targets().get(1).tokens()).isEmpty();

        verify(cleanupRepository).deleteParentDependentData(parent, senior);
        verify(familyMemberRepository).deleteAll(List.of(
                primaryMember, subMember, withdrawnMember, parentMember));
        verify(familyMemberRepository).flush();
        verify(seniorRepository).delete(senior);
        verify(seniorRepository).flush();
        verify(familyRepository).delete(family);
        verify(familyRepository).flush();
    }

    @Test
    void parentWithoutSeniorStillCleansParentDependentData() {
        User parent = user(10L, Role.PARENT, UserStatus.ACTIVE);
        given(seniorRepository.findByParentUserForUpdate(parent)).willReturn(Optional.empty());

        service.withdrawParent(parent);

        verify(cleanupRepository).deleteParentDependentData(parent, null);
        verify(noticeRepository, never()).saveAndFlush(any());
        verify(recipientRepository, never()).saveAllAndFlush(any());
        verify(eventPublisher, never()).publishEvent(any());
        verify(seniorRepository, never()).delete(any());
    }

    @Test
    void noticeWithoutGuardiansIsCompletedImmediatelyAndWithdrawalSucceeds() {
        Family family = Family.builder().familyId(1L).seniorCode("ABCD-EFGH").build();
        User parent = user(10L, Role.PARENT, UserStatus.ACTIVE);
        User registrar = user(20L, Role.CHILD, UserStatus.ACTIVE);
        Senior senior = senior(100L, family, registrar, parent);
        given(seniorRepository.findByParentUserForUpdate(parent)).willReturn(Optional.of(senior));
        given(familyRepository.findByIdForUpdate(1L)).willReturn(Optional.of(family));
        given(familyMemberRepository.findAllByFamilyOrderByIdAsc(family)).willReturn(List.of());
        given(noticeRepository.findBySeniorId(100L)).willReturn(Optional.empty());
        given(noticeRepository.saveAndFlush(any(SeniorWithdrawalNotice.class)))
                .willAnswer(invocation -> {
                    SeniorWithdrawalNotice notice = invocation.getArgument(0);
                    ReflectionTestUtils.setField(notice, "seniorWithdrawalNoticeId", 500L);
                    return notice;
                });

        service.withdrawParent(parent);

        ArgumentCaptor<SeniorWithdrawalNotice> noticeCaptor =
                ArgumentCaptor.forClass(SeniorWithdrawalNotice.class);
        verify(noticeRepository).saveAndFlush(noticeCaptor.capture());
        assertThat(noticeCaptor.getValue().getCompletedAt()).isNotNull();
        verify(recipientRepository, never()).saveAllAndFlush(any());
        verify(deviceRepository, never()).findAllByUserIn(any());
        verify(cleanupRepository).deleteParentDependentData(parent, senior);
        verify(familyMemberRepository).deleteAll(List.of());
        verify(familyMemberRepository).flush();
        verify(seniorRepository).delete(senior);
        verify(familyRepository).delete(family);
    }

    private User user(Long id, Role role, UserStatus status) {
        return User.builder()
                .usersId(id)
                .loginId("user-" + id)
                .email("user-" + id + "@example.com")
                .password("encoded")
                .name("사용자" + id)
                .role(role)
                .status(status)
                .build();
    }

    private Senior senior(Long id, Family family, User registrar, User parent) {
        return Senior.builder()
                .seniorId(id)
                .name("김영희")
                .birth(LocalDate.of(1950, 1, 1))
                .phoneNumber("010-1234-5678")
                .family(family)
                .registeredBy(registrar)
                .parentUser(parent)
                .build();
    }

    private FamilyMember member(
            Long id,
            User user,
            Family family,
            ManagerType managerType,
            SeniorRelation relation,
            String customRelation
    ) {
        return FamilyMember.builder()
                .id(id)
                .user(user)
                .family(family)
                .managerType(managerType)
                .relation(relation)
                .customRelation(customRelation)
                .build();
    }
}
