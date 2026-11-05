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
