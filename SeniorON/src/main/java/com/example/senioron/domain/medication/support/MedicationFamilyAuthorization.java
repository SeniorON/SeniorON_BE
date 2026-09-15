package com.example.senioron.domain.medication.support;

import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MedicationFamilyAuthorization {

    private final UserRepository userRepository;
    private final EntityManager entityManager;

    public User getUserOrThrow(
            Long userId
    ) {
        return userRepository
                .findById(
                        userId
                )
                .orElseThrow(() ->
                        new BusinessException(
                                ErrorCode.USER_NOT_FOUND
                        )
                );
    }

    public void validateChild(
            User requester
    ) {
        if (requester.getRole() != Role.CHILD) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN
            );
        }

        Long familyCount =
                entityManager.createQuery(
                                """
                                SELECT COUNT(familyMember)
                                FROM FamilyMember familyMember
                                WHERE familyMember.user.usersId = :userId
                                """,
                                Long.class
                        )
                        .setParameter(
                                "userId",
                                requester.getUsersId()
                        )
                        .getSingleResult();

        if (familyCount == 0) {
            throw new BusinessException(
                    ErrorCode.FAMILY_NOT_FOUND
            );
        }
    }

    public User getSameFamilyParentOrThrow(
            User requester,
            Long parentUserId
    ) {
        User parentUser =
                getUserOrThrow(
                        parentUserId
                );

        if (parentUser.getRole() != Role.PARENT) {
            throw new BusinessException(
                    ErrorCode.FAMILY_MEMBER_NOT_FOUND
            );
        }

        Long sameFamilyCount =
                entityManager.createQuery(
                                """
                                SELECT COUNT(requesterMember)
                                FROM FamilyMember requesterMember
                                WHERE requesterMember.user.usersId = :requesterUserId
                                AND requesterMember.family.familyId IN (
                                    SELECT parentMember.family.familyId
                                    FROM FamilyMember parentMember
                                    WHERE parentMember.user.usersId = :parentUserId
                                )
                                """,
                                Long.class
                        )
                        .setParameter(
                                "requesterUserId",
                                requester.getUsersId()
                        )
                        .setParameter(
                                "parentUserId",
                                parentUserId
                        )
                        .getSingleResult();

        if (sameFamilyCount == 0) {
            throw new BusinessException(
                    ErrorCode.FAMILY_MEMBER_NOT_FOUND
            );
        }

        return parentUser;
    }
}