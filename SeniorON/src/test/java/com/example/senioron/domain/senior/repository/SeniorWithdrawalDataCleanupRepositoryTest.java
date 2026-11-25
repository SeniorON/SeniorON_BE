package com.example.senioron.domain.senior.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.senioron.domain.device.entity.Device;
import com.example.senioron.domain.event.entity.Event;
import com.example.senioron.domain.event.entity.EventType;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyMember;
import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.entity.FamilyPhotoSenior;
import com.example.senioron.domain.family.entity.FamilyPhotoView;
import com.example.senioron.domain.health.entity.Health;
import com.example.senioron.domain.home.entity.ActionType;
import com.example.senioron.domain.home.entity.Home;
import com.example.senioron.domain.home.entity.HomeSetting;
import com.example.senioron.domain.hospital.entity.Hospital;
import com.example.senioron.domain.hospital.entity.HospitalReminderType;
import com.example.senioron.domain.inactivity.entity.InactivitySetting;
import com.example.senioron.domain.medication.entity.Medication;
import com.example.senioron.domain.medication.entity.MedicationLog;
import com.example.senioron.domain.medication.entity.MedicationRepeatEndType;
import com.example.senioron.domain.medication.entity.MedicationRepeatType;
import com.example.senioron.domain.notification.entity.Notification;
import com.example.senioron.domain.notification.entity.NotificationSetting;
import com.example.senioron.domain.notification.entity.NotificationType;
import com.example.senioron.domain.permission.entity.SeniorPermissionSetting;
import com.example.senioron.domain.receipt.entity.Receipt;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.entity.SeniorReloginRequest;
import com.example.senioron.domain.senior.entity.SeniorReloginRequestStatus;
import com.example.senioron.domain.user.entity.AccountRecoveryPurpose;
import com.example.senioron.domain.user.entity.AccountRecoveryVerificationCode;
import com.example.senioron.domain.user.entity.EmailVerificationRateLimit;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.SignupEmailVerificationCode;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.entity.UserStatus;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest(showSql = false)
@Import(SeniorWithdrawalDataCleanupRepository.class)
class SeniorWithdrawalDataCleanupRepositoryTest {

    @Autowired private EntityManager entityManager;
    @Autowired private SeniorWithdrawalDataCleanupRepository cleanupRepository;

    @Test
    void deletesOnlyTargetParentAndSeniorDependentDataAndKeepsSharedData() {
        User registrar = persist(user("registrar", Role.CHILD));
        User target = persist(user("target", Role.PARENT));
        User other = persist(user("other", Role.PARENT));
        Family targetFamily = persist(Family.builder().seniorCode("TARGET-1").build());
        Family otherFamily = persist(Family.builder().seniorCode("OTHER-01").build());
        Senior targetSenior = persist(senior("탈퇴 시니어", targetFamily, registrar, target));
        Senior otherSenior = persist(senior("유지 시니어", otherFamily, registrar, other));
        Device targetDevice = persist(Device.builder()
                .user(target).deviceIdentifier("target-device").deviceToken("target-token").build());
        Device otherDevice = persist(Device.builder()
                .user(other).deviceIdentifier("other-device").deviceToken("other-token").build());

        Event targetEvent = persist(Event.builder()
                .user(target).triggeredUser(target).senior(targetSenior).eventType(EventType.SOS).build());
        Event otherEvent = persist(Event.builder()
                .user(other).triggeredUser(other).senior(otherSenior).eventType(EventType.SOS).build());
        persist(Notification.builder()
                .event(targetEvent).sendUser(target).receiverUser(registrar)
                .notificationType(NotificationType.SOS).title("target").body("target").isRead(false).build());
        persist(Notification.builder()
                .event(otherEvent).sendUser(other).receiverUser(registrar)
                .notificationType(NotificationType.SOS).title("other").body("other").isRead(false).build());

        Medication targetMedication = persist(medication(target, "target-medication"));
        Medication otherMedication = persist(medication(other, "other-medication"));
        persist(medicationLog(target, targetMedication));
        persist(medicationLog(other, otherMedication));
        persist(hospital(target, targetDevice, "target-hospital"));
        persist(hospital(other, otherDevice, "other-hospital"));
        persist(Home.builder().user(target).device(targetDevice).buttonOrder(1)
                .buttonName("target-home").actionType(ActionType.DEFAULT).build());
        persist(Home.builder().user(other).device(otherDevice).buttonOrder(1)
                .buttonName("other-home").actionType(ActionType.DEFAULT).build());
        persist(Health.builder().user(target).device(targetDevice).recordDate(LocalDate.now()).build());
        persist(Health.builder().user(other).device(otherDevice).recordDate(LocalDate.now()).build());
        persist(Receipt.builder().user(target).device(targetDevice).hospitalName("target-receipt").build());
        persist(Receipt.builder().user(other).device(otherDevice).hospitalName("other-receipt").build());

        persist(HomeSetting.builder().user(target).build());
        persist(HomeSetting.builder().user(other).build());
        persist(InactivitySetting.builder().user(target).build());
        persist(InactivitySetting.builder().user(other).build());
        FamilyMember targetMember = persist(FamilyMember.builder()
                .user(registrar).family(targetFamily).managerType(ManagerType.PRIMARY).build());
        FamilyMember otherMember = persist(FamilyMember.builder()
                .user(registrar).family(otherFamily).managerType(ManagerType.PRIMARY).build());
        persist(NotificationSetting.builder().familyMember(targetMember).build());
        persist(NotificationSetting.builder().familyMember(otherMember).build());
        persist(recoveryCode(target));
        persist(recoveryCode(other));
        persist(signupVerificationCode(target.getEmail()));
        persist(signupVerificationCode(other.getEmail()));
        persist(emailRateLimit("email-verification:email:cooldown:" + target.getEmail()));
        persist(emailRateLimit("email-verification:email:10m:" + target.getEmail()));
        persist(emailRateLimit("email-verification:email:day:" + target.getEmail() + ":2026-11-04"));
        persist(emailRateLimit("email-verification:email:cooldown:" + other.getEmail()));

        FamilyPhoto sharedPhoto = persist(FamilyPhoto.builder()
                .user(registrar).imageKey("shared-image").idempotencyKey("shared-key").build());
        persist(FamilyPhotoSenior.builder().familyPhoto(sharedPhoto).senior(targetSenior).build());
        persist(FamilyPhotoSenior.builder().familyPhoto(sharedPhoto).senior(otherSenior).build());
        persist(FamilyPhotoView.builder().familyPhoto(sharedPhoto).parent(target).build());
        persist(FamilyPhotoView.builder().familyPhoto(sharedPhoto).parent(other).build());
        persist(SeniorPermissionSetting.builder().senior(targetSenior).build());
        persist(SeniorPermissionSetting.builder().senior(otherSenior).build());
        persist(SeniorReloginRequest.builder()
                .senior(targetSenior).device(targetDevice).status(SeniorReloginRequestStatus.PENDING)
                .expiresAt(LocalDateTime.now().plusMinutes(5)).build());
        persist(SeniorReloginRequest.builder()
                .senior(otherSenior).device(otherDevice).status(SeniorReloginRequestStatus.PENDING)
                .expiresAt(LocalDateTime.now().plusMinutes(5)).build());

        entityManager.flush();

        cleanupRepository.deleteParentDependentData(target, targetSenior);
        entityManager.clear();

        assertTargetDeletedAndOtherKept("Event", "user");
        assertTargetDeletedAndOtherKept("Medication", "user");
        assertTargetDeletedAndOtherKept("MedicationLog", "user");
        assertTargetDeletedAndOtherKept("Hospital", "user");
        assertTargetDeletedAndOtherKept("Home", "user");
        assertTargetDeletedAndOtherKept("Health", "user");
        assertTargetDeletedAndOtherKept("Receipt", "user");
        assertTargetDeletedAndOtherKept("HomeSetting", "user");
        assertTargetDeletedAndOtherKept("InactivitySetting", "user");
        assertTargetDeletedAndOtherKept("AccountRecoveryVerificationCode", "user");
        assertTargetDeletedAndOtherKept("FamilyPhotoView", "parent");
        assertThat(countByEmail("SignupEmailVerificationCode", target.getEmail())).isZero();
        assertThat(countByEmail("SignupEmailVerificationCode", other.getEmail())).isEqualTo(1);
        assertThat(countRateLimitsContaining(target.getEmail())).isZero();
        assertThat(countRateLimitsContaining(other.getEmail())).isEqualTo(1);

        assertThat(count("Notification")).isEqualTo(1);
        assertThat(countBySenior("SeniorReloginRequest", targetSenior)).isZero();
        assertThat(countBySenior("SeniorReloginRequest", otherSenior)).isEqualTo(1);
        assertThat(countBySenior("SeniorPermissionSetting", targetSenior)).isZero();
        assertThat(countBySenior("SeniorPermissionSetting", otherSenior)).isEqualTo(1);
        assertThat(countBySenior("FamilyPhotoSenior", targetSenior)).isZero();
        assertThat(countBySenior("FamilyPhotoSenior", otherSenior)).isEqualTo(1);
        assertThat(countNotificationSettings(targetFamily)).isZero();
        assertThat(countNotificationSettings(otherFamily)).isEqualTo(1);

        assertThat(count("FamilyPhoto")).isEqualTo(1);
        assertThat(count("Device")).isEqualTo(2);
        assertThat(count("User")).isEqualTo(3);
        assertThat(count("Senior")).isEqualTo(2);
        assertThat(count("Family")).isEqualTo(2);
    }

    private void assertTargetDeletedAndOtherKept(String entityName, String userField) {
        assertThat(countByUser(entityName, userField, "target")).isZero();
        assertThat(countByUser(entityName, userField, "other")).isEqualTo(1);
    }

    private long count(String entityName) {
        return entityManager.createQuery("SELECT COUNT(entity) FROM " + entityName + " entity", Long.class)
                .getSingleResult();
    }

    private long countByUser(String entityName, String userField, String loginId) {
        return entityManager.createQuery(
                        "SELECT COUNT(entity) FROM " + entityName + " entity "
                                + "WHERE entity." + userField + ".loginId = :loginId",
                        Long.class)
                .setParameter("loginId", loginId)
                .getSingleResult();
    }

    private long countBySenior(String entityName, Senior senior) {
        return entityManager.createQuery(
                        "SELECT COUNT(entity) FROM " + entityName + " entity WHERE entity.senior = :senior",
                        Long.class)
                .setParameter("senior", senior)
                .getSingleResult();
    }

    private long countNotificationSettings(Family family) {
        return entityManager.createQuery(
                        "SELECT COUNT(setting) FROM NotificationSetting setting "
                                + "WHERE setting.familyMember.family = :family",
                        Long.class)
                .setParameter("family", family)
                .getSingleResult();
    }

    private long countByEmail(String entityName, String email) {
        return entityManager.createQuery(
                        "SELECT COUNT(entity) FROM " + entityName + " entity WHERE entity.email = :email",
                        Long.class)
                .setParameter("email", email)
                .getSingleResult();
    }

    private long countRateLimitsContaining(String email) {
        return entityManager.createQuery("""
                        SELECT COUNT(rateLimit)
                        FROM EmailVerificationRateLimit rateLimit
                        WHERE LOCATE(:email, rateLimit.rateLimitKey) > 0
                        """, Long.class)
                .setParameter("email", email)
                .getSingleResult();
    }

    private User user(String loginId, Role role) {
        return User.builder()
                .loginId(loginId)
                .email(loginId + "@example.com")
                .password("encoded")
                .name(loginId)
                .role(role)
                .status(UserStatus.ACTIVE)
                .build();
    }

    private Senior senior(String name, Family family, User registrar, User parent) {
        return Senior.builder()
                .name(name)
                .birth(LocalDate.of(1950, 1, 1))
                .phoneNumber("010-0000-0000")
                .family(family)
                .registeredBy(registrar)
                .parentUser(parent)
                .build();
    }

    private Medication medication(User user, String name) {
        return Medication.builder()
                .user(user)
                .medicineName(name)
                .medicineTime(LocalTime.of(9, 0))
                .medicineDays("MONDAY")
                .medicationGroupId(name + "-group")
                .scheduleStartDate(LocalDate.now())
                .repeatType(MedicationRepeatType.DAILY)
                .repeatInterval(1)
                .repeatEndType(MedicationRepeatEndType.ONGOING)
                .effectiveFrom(LocalDateTime.now())
                .build();
    }

    private MedicationLog medicationLog(User user, Medication medication) {
        return MedicationLog.builder()
                .user(user)
                .medication(medication)
                .plannedDate(LocalDate.now())
                .plannedTime(LocalTime.of(9, 0))
                .isTaken(false)
                .build();
    }

    private Hospital hospital(User user, Device device, String name) {
        return Hospital.builder()
                .user(user)
                .device(device)
                .hospitalName(name)
                .department("내과")
                .scheduleDate(LocalDate.now())
                .scheduleTime(LocalTime.NOON)
                .reminderType(HospitalReminderType.NONE)
                .build();
    }

    private AccountRecoveryVerificationCode recoveryCode(User user) {
        return AccountRecoveryVerificationCode.builder()
                .user(user)
                .purpose(AccountRecoveryPurpose.PASSWORD_RESET)
                .codeHash("hash-" + user.getLoginId())
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();
    }

    private SignupEmailVerificationCode signupVerificationCode(String email) {
        LocalDateTime now = LocalDateTime.now();
        return SignupEmailVerificationCode.builder()
                .email(email)
                .codeHash("hash-" + email)
                .issuedAt(now)
                .expiresAt(now.plusMinutes(5))
                .build();
    }

    private EmailVerificationRateLimit emailRateLimit(String key) {
        LocalDateTime now = LocalDateTime.now();
        return EmailVerificationRateLimit.builder()
                .rateLimitKey(key)
                .requestCount(1)
                .windowStartedAt(now)
                .expiresAt(now.plusDays(1))
                .build();
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }
}
