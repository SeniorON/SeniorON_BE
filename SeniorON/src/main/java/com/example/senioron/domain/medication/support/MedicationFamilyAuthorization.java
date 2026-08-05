package com.example.senioron.domain.medication.support;

import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MedicationFamilyAuthorization {

    private final UserRepository userRepository;

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

        if (requester.getFamily() == null) {
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

        boolean belongsToSameFamily =
                parentUser.getFamily() != null
                        && Objects.equals(
                        requester.getFamily()
                                .getFamilyId(),
                        parentUser.getFamily()
                                .getFamilyId()
                );

        if (!belongsToSameFamily) {
            throw new BusinessException(
                    ErrorCode.FAMILY_MEMBER_NOT_FOUND
            );
        }

        return parentUser;
    }
}