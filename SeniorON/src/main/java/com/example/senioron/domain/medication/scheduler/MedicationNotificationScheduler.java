package com.example.senioron.domain.medication.scheduler;

import com.example.senioron.domain.device.entity.Device;
import com.example.senioron.domain.device.repository.DeviceRepository;
import com.example.senioron.domain.event.util.FcmSender;
import com.example.senioron.domain.medication.entity.MedicationLog;
import com.example.senioron.domain.medication.repository.MedicationLogRepository;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
            30L;

    private final MedicationLogRepository medicationLogRepository;
    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final FcmSender fcmSender;
    private final PlatformTransactionManager transactionManager;

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
                                List.copyOf(devices),
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
            int successCount = 0;
            int failureCount = 0;
            int skippedCount = 0;

            for (DeviceTarget device
                    : notification.devices()) {
                String deviceToken =
                        device.deviceToken();

                if (deviceToken == null
                        || deviceToken.isBlank()) {
                    skippedCount++;
                    continue;
                }

                try {
                    fcmSender.sendData(
                            deviceToken,
                            notification.data()
                    );

                    successCount++;
                } catch (RuntimeException e) {
                    failureCount++;

                    log.error(
                            "부모 복약 시간 FCM 발송 요청 실패. "
                                    + "deviceId={}, medicationLogId={}",
                            device.deviceId(),
                            notification.medicationLogId(),
                            e
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

            Set<Object> targetFamilies =
                    new LinkedHashSet<>();

            for (MedicationLog medicationLog : medicationLogs) {
                User parentUser =
                        medicationLog.getUser();

                parentUsersById.putIfAbsent(
                        parentUser.getUsersId(),
                        parentUser
                );

                if (parentUser.getFamily() != null) {
                    targetFamilies.add(
                            parentUser.getFamily()
                    );
                }
            }

            if (targetFamilies.isEmpty()) {
                return List.of();
            }

            List<User> childUsers =
                    userRepository.findAll()
                            .stream()
                            .filter(user ->
                                    user.getRole()
                                            == Role.CHILD
                            )
                            .filter(user ->
                                    user.getFamily()
                                            != null
                            )
                            .filter(user ->
                                    targetFamilies.contains(
                                            user.getFamily()
                                    )
                            )
                            .toList();

            if (childUsers.isEmpty()) {
                return List.of();
            }

            Map<Object, List<User>>
                    childUsersByFamily =
                    new LinkedHashMap<>();

            for (User childUser : childUsers) {
                childUsersByFamily
                        .computeIfAbsent(
                                childUser.getFamily(),
                                key -> new ArrayList<>()
                        )
                        .add(childUser);
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

                if (parentUser.getFamily() == null) {
                    continue;
                }

                List<User> familyChildUsers =
                        childUsersByFamily.getOrDefault(
                                parentUser.getFamily(),
                                List.of()
                        );

                if (familyChildUsers.isEmpty()) {
                    continue;
                }

                List<DeviceTarget> familyChildDevices =
                        new ArrayList<>();

                for (User childUser : familyChildUsers) {
                    familyChildDevices.addAll(
                            devicesByUserId.getOrDefault(
                                    childUser.getUsersId(),
                                    List.of()
                            )
                    );
                }

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

    private void sendChildMissedNotifications(
            List<ChildMissedNotification> notifications
    ) {
        for (ChildMissedNotification notification
                : notifications) {
            int successCount = 0;
            int failureCount = 0;
            int skippedCount = 0;

            for (DeviceTarget device
                    : notification.devices()) {
                String deviceToken =
                        device.deviceToken();

                if (deviceToken == null
                        || deviceToken.isBlank()) {
                    skippedCount++;
                    continue;
                }

                try {
                    fcmSender.send(
                            deviceToken,
                            notification.title(),
                            notification.body()
                    );

                    successCount++;
                } catch (RuntimeException e) {
                    failureCount++;

                    log.error(
                            "자녀 미복용 FCM 발송 요청 실패. "
                                    + "deviceId={}, medicationLogId={}",
                            device.deviceId(),
                            notification.medicationLogId(),
                            e
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
                            key -> new ArrayList<>()
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

        transactionTemplate.setReadOnly(true);

        T result =
                transactionTemplate.execute(
                        status -> action.get()
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
        if (value == null || value.isBlank()) {
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