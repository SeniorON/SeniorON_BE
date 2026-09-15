package com.example.senioron.domain.medication.scheduler;

import com.example.senioron.domain.device.entity.Device;
import com.example.senioron.domain.device.repository.DeviceRepository;
import com.example.senioron.domain.event.util.FcmSender;
import com.example.senioron.domain.medication.entity.MedicationLog;
import com.example.senioron.domain.medication.repository.MedicationLogRepository;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class MedicationNotificationScheduler {

    private static final ZoneId KOREA_ZONE_ID =
            ZoneId.of("Asia/Seoul");

    private static final long MISSED_DELAY_MINUTES =
            120L;

    private final MedicationLogRepository medicationLogRepository;
    private final DeviceRepository deviceRepository;
    private final FcmSender fcmSender;
    private final PlatformTransactionManager transactionManager;
    private final EntityManager entityManager;

    @Scheduled(
            cron = "0 * * * * *",
            zone = "Asia/Seoul"
    )
    public void processMedicationNotifications() {
        LocalDateTime currentMinute =
                LocalDateTime.now(KOREA_ZONE_ID)
                        .withSecond(0)
                        .withNano(0);

        List<ParentReminderNotification> parentNotifications =
                loadParentMedicationReminders(
                        currentMinute
                );

        sendParentMedicationReminders(
                parentNotifications
        );

        LocalDateTime missedMedicationTime =
                currentMinute.minusMinutes(
                        MISSED_DELAY_MINUTES
                );

        List<ChildMissedNotification> childNotifications =
                loadChildMissedNotifications(
                        missedMedicationTime
                );

        sendChildMissedNotifications(
                childNotifications
        );
    }

    private List<ParentReminderNotification>
    loadParentMedicationReminders(
            LocalDateTime targetDateTime
    ) {
        return executeReadOnlyTransaction(() -> {
            List<MedicationLog> medicationLogs =
                    medicationLogRepository
                            .findByPlannedDateAndPlannedTimeAndIsTakenFalseOrderByMedicationLogIdAsc(
                                    targetDateTime.toLocalDate(),
                                    targetDateTime.toLocalTime()
                            );

            if (medicationLogs.isEmpty()) {
                return List.of();
            }

            Map<Long, User> parentUsersById =
                    new LinkedHashMap<>();

            for (MedicationLog medicationLog : medicationLogs) {
                User parentUser =
                        medicationLog.getUser();

                parentUsersById.putIfAbsent(
                        parentUser.getUsersId(),
                        parentUser
                );
            }

            List<Device> parentDevices =
                    deviceRepository.findAllByUserIn(
                            new ArrayList<>(
                                    parentUsersById.values()
                            )
                    );

            Map<Long, List<DeviceTarget>>
                    devicesByUserId =
                    groupDevicesByUserId(
                            parentDevices
                    );

            List<ParentReminderNotification>
                    notifications =
                    new ArrayList<>();

            for (MedicationLog medicationLog : medicationLogs) {
                User parentUser =
                        medicationLog.getUser();

                String medicineName =
                        getSafeValue(
                                medicationLog
                                        .getMedication()
                                        .getMedicineName(),
                                "약"
                        );

                String ingredientName =
                        getSafeValue(
                                medicationLog
                                        .getMedication()
                                        .getIngredientName(),
                                ""
                        );

                String title =
                        "약 드실 시간이에요!";

                String body =
                        medicineName
                                + "을(를) 복용해 주세요.";

                Map<String, String> data =
                        Map.of(
                                "type",
                                "MEDICATION_REMINDER",
                                "title",
                                title,
                                "body",
                                body,
                                "medicationLogId",
                                medicationLog
                                        .getMedicationLogId()
                                        .toString(),
                                "medicineName",
                                medicineName,
                                "ingredientName",
                                ingredientName,
                                "plannedDate",
                                medicationLog
                                        .getPlannedDate()
                                        .toString(),
                                "plannedTime",
                                medicationLog
                                        .getPlannedTime()
                                        .toString()
                        );

                List<DeviceTarget> devices =
                        devicesByUserId.getOrDefault(
                                parentUser.getUsersId(),
                                List.of()
                        );

                notifications.add(
                        new ParentReminderNotification(
                                parentUser.getUsersId(),
                                medicationLog
                                        .getMedicationLogId(),
                                List.copyOf(
                                        devices
                                ),
                                data
                        )
                );
            }

            return notifications;
        });
    }

    private void sendParentMedicationReminders(
            List<ParentReminderNotification> notifications
    ) {
        for (ParentReminderNotification notification
                : notifications) {

            if (!isMedicationStillUntaken(
                    notification.medicationLogId()
            )) {
                log.info(
                        "이미 복용 완료되어 부모 복약 알림 발송을 건너뜁니다. "
                                + "parentUserId={}, medicationLogId={}",
                        notification.parentUserId(),
                        notification.medicationLogId()
                );

                continue;
            }

            int successCount = 0;
            int failureCount = 0;
            int skippedCount = 0;

            for (DeviceTarget device
                    : notification.devices()) {

                if (!isMedicationStillUntaken(
                        notification.medicationLogId()
                )) {
                    skippedCount++;

                    log.info(
                            "알림 발송 직전 복용 완료가 확인되어 부모 복약 알림을 건너뜁니다. "
                                    + "deviceId={}, medicationLogId={}",
                            device.deviceId(),
                            notification.medicationLogId()
                    );

                    continue;
                }

                String deviceToken =
                        device.deviceToken();

                if (deviceToken == null
                        || deviceToken.isBlank()) {
                    skippedCount++;
                    continue;
                }

                try {
                    boolean sent =
                            fcmSender.sendData(
                                    deviceToken,
                                    notification.data()
                            );

                    if (sent) {
                        successCount++;
                    } else {
                        failureCount++;
                    }
                } catch (RuntimeException exception) {
                    failureCount++;

                    log.error(
                            "부모 복약 시간 FCM 발송 요청 실패. "
                                    + "deviceId={}, medicationLogId={}",
                            device.deviceId(),
                            notification.medicationLogId(),
                            exception
                    );
                }
            }

            log.info(
                    "부모 복약 시간 푸시 요청 처리 종료. "
                            + "parentUserId={}, medicationLogId={}, "
                            + "deviceCount={}, requestSuccessCount={}, "
                            + "requestFailureCount={}, skippedCount={}",
                    notification.parentUserId(),
                    notification.medicationLogId(),
                    notification.devices().size(),
                    successCount,
                    failureCount,
                    skippedCount
            );
        }
    }

    private List<ChildMissedNotification>
    loadChildMissedNotifications(
            LocalDateTime targetDateTime
    ) {
        return executeReadOnlyTransaction(() -> {
            List<MedicationLog> medicationLogs =
                    medicationLogRepository
                            .findByPlannedDateAndPlannedTimeAndIsTakenFalseOrderByMedicationLogIdAsc(
                                    targetDateTime.toLocalDate(),
                                    targetDateTime.toLocalTime()
                            );

            if (medicationLogs.isEmpty()) {
                return List.of();
            }

            Map<Long, User> parentUsersById =
                    new LinkedHashMap<>();

            for (MedicationLog medicationLog : medicationLogs) {
                User parentUser =
                        medicationLog.getUser();

                parentUsersById.putIfAbsent(
                        parentUser.getUsersId(),
                        parentUser
                );
            }

            Map<Long, List<Long>>
                    familyIdsByParentUserId =
                    findFamilyIdsByUserIds(
                            new ArrayList<>(
                                    parentUsersById.keySet()
                            )
                    );

            if (familyIdsByParentUserId.isEmpty()) {
                return List.of();
            }

            List<Long> targetFamilyIds =
                    familyIdsByParentUserId.values()
                            .stream()
                            .flatMap(List::stream)
                            .distinct()
                            .toList();

            Map<Long, List<User>>
                    childUsersByFamilyId =
                    findChildUsersByFamilyIds(
                            targetFamilyIds
                    );

            if (childUsersByFamilyId.isEmpty()) {
                return List.of();
            }

            Map<Long, User> uniqueChildUsersById =
                    new LinkedHashMap<>();

            childUsersByFamilyId.values()
                    .stream()
                    .flatMap(List::stream)
                    .forEach(childUser ->
                            uniqueChildUsersById.putIfAbsent(
                                    childUser.getUsersId(),
                                    childUser
                            )
                    );

            List<User> childUsers =
                    new ArrayList<>(
                            uniqueChildUsersById.values()
                    );

            if (childUsers.isEmpty()) {
                return List.of();
            }

            List<Device> childDevices =
                    deviceRepository.findAllByUserIn(
                            childUsers
                    );

            Map<Long, List<DeviceTarget>>
                    devicesByUserId =
                    groupDevicesByUserId(
                            childDevices
                    );

            List<ChildMissedNotification>
                    notifications =
                    new ArrayList<>();

            for (MedicationLog medicationLog : medicationLogs) {
                User parentUser =
                        medicationLog.getUser();

                List<Long> familyIds =
                        familyIdsByParentUserId
                                .getOrDefault(
                                        parentUser.getUsersId(),
                                        List.of()
                                );

                if (familyIds.isEmpty()) {
                    continue;
                }

                Map<Long, User>
                        familyChildUsersById =
                        new LinkedHashMap<>();

                for (Long familyId : familyIds) {
                    for (User childUser :
                            childUsersByFamilyId.getOrDefault(
                                    familyId,
                                    List.of()
                            )) {
                        familyChildUsersById.putIfAbsent(
                                childUser.getUsersId(),
                                childUser
                        );
                    }
                }

                if (familyChildUsersById.isEmpty()) {
                    continue;
                }

                Map<Long, DeviceTarget>
                        familyChildDevicesById =
                        new LinkedHashMap<>();

                for (User childUser :
                        familyChildUsersById.values()) {
                    for (DeviceTarget device :
                            devicesByUserId.getOrDefault(
                                    childUser.getUsersId(),
                                    List.of()
                            )) {
                        familyChildDevicesById.putIfAbsent(
                                device.deviceId(),
                                device
                        );
                    }
                }

                List<DeviceTarget> familyChildDevices =
                        new ArrayList<>(
                                familyChildDevicesById.values()
                        );

                String parentName =
                        getSafeValue(
                                parentUser.getName(),
                                "부모님"
                        );

                String medicineName =
                        getSafeValue(
                                medicationLog
                                        .getMedication()
                                        .getMedicineName(),
                                "약"
                        );

                String title =
                        "약 미복용 알림";

                String body =
                        parentName
                                + "님이 아직 "
                                + medicineName
                                + "을(를) 복용하지 않았어요.";

                notifications.add(
                        new ChildMissedNotification(
                                parentUser.getUsersId(),
                                medicationLog
                                        .getMedicationLogId(),
                                List.copyOf(
                                        familyChildDevices
                                ),
                                title,
                                body
                        )
                );
            }

            return notifications;
        });
    }

    private Map<Long, List<Long>> findFamilyIdsByUserIds(
            List<Long> userIds
    ) {
        Map<Long, List<Long>> familyIdsByUserId =
                new LinkedHashMap<>();

        if (userIds.isEmpty()) {
            return familyIdsByUserId;
        }

        List<Object[]> rows =
                entityManager.createQuery(
                                """
                                SELECT familyMember.user.usersId,
                                       familyMember.family.familyId
                                FROM FamilyMember familyMember
                                WHERE familyMember.user.usersId IN :userIds
                                """,
                                Object[].class
                        )
                        .setParameter(
                                "userIds",
                                userIds
                        )
                        .getResultList();

        for (Object[] row : rows) {
            Long userId =
                    (Long) row[0];

            Long familyId =
                    (Long) row[1];

            familyIdsByUserId
                    .computeIfAbsent(
                            userId,
                            ignored ->
                                    new ArrayList<>()
                    )
                    .add(
                            familyId
                    );
        }

        return familyIdsByUserId;
    }

    private Map<Long, List<User>> findChildUsersByFamilyIds(
            List<Long> familyIds
    ) {
        Map<Long, List<User>> childUsersByFamilyId =
                new LinkedHashMap<>();

        if (familyIds.isEmpty()) {
            return childUsersByFamilyId;
        }

        List<Object[]> rows =
                entityManager.createQuery(
                                """
                                SELECT familyMember.family.familyId,
                                       familyMember.user
                                FROM FamilyMember familyMember
                                WHERE familyMember.family.familyId IN :familyIds
                                AND familyMember.user.role = :role
                                """,
                                Object[].class
                        )
                        .setParameter(
                                "familyIds",
                                familyIds
                        )
                        .setParameter(
                                "role",
                                Role.CHILD
                        )
                        .getResultList();

        for (Object[] row : rows) {
            Long familyId =
                    (Long) row[0];

            User childUser =
                    (User) row[1];

            childUsersByFamilyId
                    .computeIfAbsent(
                            familyId,
                            ignored ->
                                    new ArrayList<>()
                    )
                    .add(
                            childUser
                    );
        }

        return childUsersByFamilyId;
    }

    private void sendChildMissedNotifications(
            List<ChildMissedNotification> notifications
    ) {
        for (ChildMissedNotification notification
                : notifications) {

            if (!isMedicationStillUntaken(
                    notification.medicationLogId()
            )) {
                log.info(
                        "이미 복용 완료되어 자녀 미복용 알림 발송을 건너뜁니다. "
                                + "parentUserId={}, medicationLogId={}",
                        notification.parentUserId(),
                        notification.medicationLogId()
                );

                continue;
            }

            int successCount = 0;
            int failureCount = 0;
            int skippedCount = 0;

            for (DeviceTarget device
                    : notification.devices()) {

                if (!isMedicationStillUntaken(
                        notification.medicationLogId()
                )) {
                    skippedCount++;

                    log.info(
                            "알림 발송 직전 복용 완료가 확인되어 자녀 미복용 알림을 건너뜁니다. "
                                    + "deviceId={}, medicationLogId={}",
                            device.deviceId(),
                            notification.medicationLogId()
                    );

                    continue;
                }

                String deviceToken =
                        device.deviceToken();

                if (deviceToken == null
                        || deviceToken.isBlank()) {
                    skippedCount++;
                    continue;
                }

                try {
                    boolean sent =
                            fcmSender.send(
                                    deviceToken,
                                    notification.title(),
                                    notification.body()
                            );

                    if (sent) {
                        successCount++;
                    } else {
                        failureCount++;
                    }
                } catch (RuntimeException exception) {
                    failureCount++;

                    log.error(
                            "자녀 미복용 FCM 발송 요청 실패. "
                                    + "deviceId={}, medicationLogId={}",
                            device.deviceId(),
                            notification.medicationLogId(),
                            exception
                    );
                }
            }

            log.info(
                    "자녀 미복용 푸시 요청 처리 종료. "
                            + "parentUserId={}, medicationLogId={}, "
                            + "childDeviceCount={}, requestSuccessCount={}, "
                            + "requestFailureCount={}, skippedCount={}",
                    notification.parentUserId(),
                    notification.medicationLogId(),
                    notification.devices().size(),
                    successCount,
                    failureCount,
                    skippedCount
            );
        }
    }

    private boolean isMedicationStillUntaken(
            Long medicationLogId
    ) {
        return medicationLogRepository
                .existsByMedicationLogIdAndIsTakenFalse(
                        medicationLogId
                );
    }

    private Map<Long, List<DeviceTarget>>
    groupDevicesByUserId(
            List<Device> devices
    ) {
        Map<Long, List<DeviceTarget>>
                devicesByUserId =
                new LinkedHashMap<>();

        for (Device device : devices) {
            Long userId =
                    device.getUser()
                            .getUsersId();

            devicesByUserId
                    .computeIfAbsent(
                            userId,
                            key ->
                                    new ArrayList<>()
                    )
                    .add(
                            new DeviceTarget(
                                    device.getDeviceId(),
                                    device.getDeviceToken()
                            )
                    );
        }

        return devicesByUserId;
    }

    private <T> T executeReadOnlyTransaction(
            Supplier<T> action
    ) {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(
                        transactionManager
                );

        transactionTemplate.setReadOnly(
                true
        );

        T result =
                transactionTemplate.execute(
                        status ->
                                action.get()
                );

        if (result == null) {
            throw new IllegalStateException(
                    "읽기 전용 트랜잭션 결과가 없습니다."
            );
        }

        return result;
    }

    private String getSafeValue(
            String value,
            String fallback
    ) {
        if (value == null
                || value.isBlank()) {
            return fallback;
        }

        return value;
    }

    private record DeviceTarget(
            Long deviceId,
            String deviceToken
    ) {
    }

    private record ParentReminderNotification(
            Long parentUserId,
            Long medicationLogId,
            List<DeviceTarget> devices,
            Map<String, String> data
    ) {
    }

    private record ChildMissedNotification(
            Long parentUserId,
            Long medicationLogId,
            List<DeviceTarget> devices,
            String title,
            String body
    ) {
    }
}