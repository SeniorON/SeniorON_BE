package com.example.senioron.domain.senior.repository;

import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.user.entity.User;
import jakarta.persistence.EntityManager;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * PARENT 탈퇴 시 여러 도메인에 흩어진 종속 데이터의 삭제 순서를 한곳에서 관리한다.
 * 공유/감사 데이터(FamilyPhoto, Inquiry, 신고)는 의도적으로 이 범위에 포함하지 않는다.
 */
@Repository
@RequiredArgsConstructor
public class SeniorWithdrawalDataCleanupRepository {

    private final EntityManager entityManager;

    public void deleteParentDependentData(User parentUser, Senior senior) {
        entityManager.flush();

        deleteNotifications(parentUser, senior);
        deleteEvents(parentUser, senior);

        execute("""
                DELETE FROM MedicationLog log
                WHERE log.user = :parentUser
                   OR log.medication IN (
                        SELECT medication
                        FROM Medication medication
                        WHERE medication.user = :parentUser
                   )
                """, parentUser);
        execute("DELETE FROM Medication medication WHERE medication.user = :parentUser", parentUser);

        execute("DELETE FROM Hospital hospital WHERE hospital.user = :parentUser", parentUser);
        execute("DELETE FROM Home home WHERE home.user = :parentUser", parentUser);
        execute("DELETE FROM Health health WHERE health.user = :parentUser", parentUser);
        execute("DELETE FROM Receipt receipt WHERE receipt.user = :parentUser", parentUser);

        execute("DELETE FROM HomeSetting setting WHERE setting.user = :parentUser", parentUser);
        execute("DELETE FROM InactivitySetting setting WHERE setting.user = :parentUser", parentUser);
        execute("DELETE FROM AccountRecoveryVerificationCode code WHERE code.user = :parentUser", parentUser);
        execute("DELETE FROM FamilyPhotoView view WHERE view.parent = :parentUser", parentUser);
        deleteEmailVerificationData(parentUser.getEmail());

        if (senior != null) {
            executeSenior("DELETE FROM SeniorReloginRequest request WHERE request.senior = :senior", senior);
            executeSenior("DELETE FROM SeniorPermissionSetting setting WHERE setting.senior = :senior", senior);
            executeSenior("""
                    DELETE FROM NotificationSetting setting
                    WHERE setting.familyMember IN (
                        SELECT member
                        FROM FamilyMember member, Senior senior
                        WHERE senior = :senior
                          AND member.family = senior.family
                    )
                    """, senior);
            executeSenior("DELETE FROM FamilyPhotoSenior mapping WHERE mapping.senior = :senior", senior);
        }
    }

    private void deleteNotifications(User parentUser, Senior senior) {
        execute("""
                DELETE FROM Notification notification
                WHERE notification.sendUser = :parentUser
                   OR notification.receiverUser = :parentUser
                   OR notification.event IN (
                        SELECT event
                        FROM Event event
                        WHERE event.user = :parentUser
                           OR event.triggeredUser = :parentUser
                   )
                """, parentUser);

        if (senior != null) {
            executeSenior("""
                    DELETE FROM Notification notification
                    WHERE notification.event IN (
                        SELECT event
                        FROM Event event
                        WHERE event.senior = :senior
                    )
                    """, senior);
        }
    }

    private void deleteEvents(User parentUser, Senior senior) {
        execute("""
                DELETE FROM Event event
                WHERE event.user = :parentUser
                   OR event.triggeredUser = :parentUser
                """, parentUser);

        if (senior != null) {
            executeSenior("DELETE FROM Event event WHERE event.senior = :senior", senior);
        }
    }

    private void deleteEmailVerificationData(String email) {
        if (email == null || email.isBlank()) {
            return;
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        entityManager.createQuery("""
                        DELETE FROM SignupEmailVerificationCode code
                        WHERE LOWER(code.email) = :email
                        """)
                .setParameter("email", normalizedEmail)
                .executeUpdate();

        entityManager.createQuery("""
                        DELETE FROM EmailVerificationRateLimit rateLimit
                        WHERE rateLimit.rateLimitKey = :cooldownKey
                           OR rateLimit.rateLimitKey = :shortWindowKey
                           OR LOCATE(:dailyPrefix, rateLimit.rateLimitKey) = 1
                        """)
                .setParameter(
                        "cooldownKey",
                        "email-verification:email:cooldown:" + normalizedEmail
                )
                .setParameter(
                        "shortWindowKey",
                        "email-verification:email:10m:" + normalizedEmail
                )
                .setParameter(
                        "dailyPrefix",
                        "email-verification:email:day:" + normalizedEmail + ":"
                )
                .executeUpdate();
    }

    private int execute(String jpql, User parentUser) {
        return entityManager.createQuery(jpql)
                .setParameter("parentUser", parentUser)
                .executeUpdate();
    }

    private int executeSenior(String jpql, Senior senior) {
        return entityManager.createQuery(jpql)
                .setParameter("senior", senior)
                .executeUpdate();
    }
}
