package com.example.senioron.domain.family.service;

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
import com.example.senioron.domain.user.entity.UserStatus;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FamilyConnectionService {

    private final UserRepository userRepository;
    private final FamilyRepository familyRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final SeniorRepository seniorRepository;
    private final UnusedFamilyCleanupService unusedFamilyCleanupService;

    @Transactional
    public FamilyConnectionDisconnectResponse disconnect(
            User principal,
            Long familyId
    ) {
        Family family = familyRepository.findByIdForUpdate(familyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FAMILY_NOT_FOUND));

        User currentUser = userRepository.findByIdForUpdate(principal.getUsersId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        FamilyMember currentMember = familyMemberRepository
                .findByUserAndFamilyForUpdate(currentUser, family)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.FAMILY_MEMBER_NOT_FOUND)
                );

        validateDisconnectRole(currentUser, currentMember);

        List<Senior> lockedSeniors = null;
        if (currentMember.getManagerType() == ManagerType.PRIMARY) {
            lockedSeniors = validatePrimaryDisconnect(currentMember, family);
        }

        familyMemberRepository.delete(currentMember);

        if (lockedSeniors != null) {
            familyMemberRepository.flush();
            unusedFamilyCleanupService.cleanup(family, lockedSeniors);
            return new FamilyConnectionDisconnectResponse(familyId, true);
        }

        return new FamilyConnectionDisconnectResponse(familyId, false);
    }

    @Transactional
    public void withdrawPrimaryMemberships(User user) {
        List<PrimaryWithdrawalTarget> targets = familyMemberRepository
                .findAllByUserOrderByIdAsc(user)
                .stream()
                .filter(member -> member.getManagerType() == ManagerType.PRIMARY)
                .map(member -> lockPrimaryWithdrawalTarget(user, member.getFamily().getFamilyId()))
                .toList();

        // 모든 Family를 먼저 검증해 한 곳이라도 탈퇴 불가하면 삭제를 시작하지 않는다.
        targets.forEach(this::validatePrimaryWithdrawal);

        for (PrimaryWithdrawalTarget target : targets) {
            List<FamilyMember> membersToRemove = target.familyMembers().stream()
                    .filter(member -> member.getUser().getStatus() != UserStatus.ACTIVE
                            || Objects.equals(member.getId(), target.primaryMember().getId()))
                    .toList();
            familyMemberRepository.deleteAll(membersToRemove);
            familyMemberRepository.flush();
            unusedFamilyCleanupService.cleanupAfterPrimaryWithdrawal(target.family());
        }
    }

    private PrimaryWithdrawalTarget lockPrimaryWithdrawalTarget(User user, Long familyId) {
        Family family = familyRepository.findByIdForUpdate(familyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FAMILY_NOT_FOUND));
        FamilyMember primaryMember = familyMemberRepository
                .findByUserAndFamilyForUpdate(user, family)
                .orElseThrow(() -> new BusinessException(ErrorCode.FAMILY_MEMBER_NOT_FOUND));
        List<FamilyMember> familyMembers = familyMemberRepository
                .findAllByFamilyOrderByIdAsc(family);
        return new PrimaryWithdrawalTarget(family, primaryMember, familyMembers);
    }

    private void validatePrimaryWithdrawal(PrimaryWithdrawalTarget target) {
        boolean hasOtherActiveChild = target.familyMembers().stream()
                .filter(member -> !Objects.equals(member.getId(), target.primaryMember().getId()))
                .anyMatch(member -> member.getUser().getStatus() == UserStatus.ACTIVE
                        && member.getUser().getRole() == Role.CHILD);
        if (hasOtherActiveChild) {
            throw new BusinessException(ErrorCode.PRIMARY_WITHDRAWAL_REQUIRES_TRANSFER);
        }

        boolean hasOtherActiveMember = target.familyMembers().stream()
                .filter(member -> !Objects.equals(member.getId(), target.primaryMember().getId()))
                .anyMatch(member -> member.getUser().getStatus() == UserStatus.ACTIVE);
        if (hasOtherActiveMember) {
            throw new BusinessException(ErrorCode.PRIMARY_WITHDRAWAL_SENIOR_REMAINS);
        }

        if (!seniorRepository.findAllByFamilyOrderBySeniorIdAscForUpdate(target.family()).isEmpty()) {
            throw new BusinessException(ErrorCode.PRIMARY_WITHDRAWAL_SENIOR_REMAINS);
        }
    }

    private record PrimaryWithdrawalTarget(
            Family family,
            FamilyMember primaryMember,
            List<FamilyMember> familyMembers
    ) {
    }

    private void validateDisconnectRole(
            User currentUser,
            FamilyMember currentMember
    ) {
        ManagerType managerType = currentMember.getManagerType();
        boolean disconnectableManager = managerType == ManagerType.SUB
                || managerType == ManagerType.PRIMARY;

        if (currentUser.getRole() != Role.CHILD || !disconnectableManager) {
            throw new BusinessException(
                    ErrorCode.FAMILY_CONNECTION_DISCONNECT_FORBIDDEN
            );
        }
    }

    private List<Senior> validatePrimaryDisconnect(
            FamilyMember currentMember,
            Family family
    ) {
        if (family.getFirstSeniorConnectedAt() != null) {
            throw new BusinessException(
                    ErrorCode.PRIMARY_CONNECTION_DISCONNECT_HISTORY_EXISTS
            );
        }

        List<FamilyMember> familyMembers =
                familyMemberRepository.findAllByFamilyOrderByIdAsc(family);

        boolean hasOtherGuardian = familyMembers.stream()
                .anyMatch(member ->
                        !Objects.equals(member.getId(), currentMember.getId())
                                && member.getUser().getRole() == Role.CHILD
                );
        if (hasOtherGuardian) {
            throw new BusinessException(
                    ErrorCode.PRIMARY_CONNECTION_DISCONNECT_GUARDIAN_EXISTS
            );
        }

        boolean hasParentMember = familyMembers.stream()
                .anyMatch(member -> member.getUser().getRole() == Role.PARENT);
        if (hasParentMember) {
            throw new BusinessException(
                    ErrorCode.PRIMARY_CONNECTION_DISCONNECT_PARENT_EXISTS
            );
        }

        List<Senior> lockedSeniors = seniorRepository
                .findAllByFamilyOrderBySeniorIdAscForUpdate(family);

        boolean hasLinkedParent = lockedSeniors.stream()
                .anyMatch(senior -> senior.getParentUser() != null);

        if (hasLinkedParent) {
            throw new BusinessException(
                    ErrorCode.PRIMARY_CONNECTION_DISCONNECT_PARENT_EXISTS
            );
        }

        return lockedSeniors;
    }
}
