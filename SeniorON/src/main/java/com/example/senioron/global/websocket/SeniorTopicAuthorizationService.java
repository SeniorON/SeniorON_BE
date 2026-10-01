package com.example.senioron.global.websocket;

import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeniorTopicAuthorizationService {

    private final EntityManager entityManager;

    public boolean canSubscribe(User user, Long seniorId) {
        if (user == null || seniorId == null) {
            return false;
        }

        if (user.getRole() == Role.PARENT) {
            return isLinkedParent(user.getUsersId(), seniorId);
        }

        if (user.getRole() == Role.CHILD) {
            return isSameFamilyChild(user.getUsersId(), seniorId);
        }

        return false;
    }

    private boolean isLinkedParent(Long userId, Long seniorId) {
        Long count = entityManager.createQuery(
                        """
                        SELECT COUNT(senior)
                        FROM Senior senior
                        WHERE senior.seniorId = :seniorId
                          AND senior.parentUser.usersId = :userId
                        """,
                        Long.class
                )
                .setParameter("seniorId", seniorId)
                .setParameter("userId", userId)
                .getSingleResult();

        return count > 0;
    }

    private boolean isSameFamilyChild(Long userId, Long seniorId) {
        Long count = entityManager.createQuery(
                        """
                        SELECT COUNT(familyMember)
                        FROM FamilyMember familyMember
                        WHERE familyMember.user.usersId = :userId
                          AND familyMember.family.familyId IN (
                              SELECT senior.family.familyId
                              FROM Senior senior
                              WHERE senior.seniorId = :seniorId
                          )
                        """,
                        Long.class
                )
                .setParameter("userId", userId)
                .setParameter("seniorId", seniorId)
                .getSingleResult();

        return count > 0;
    }
}
