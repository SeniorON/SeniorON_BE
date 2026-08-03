package com.example.senioron.domain.hospital.scheduler;

import com.example.senioron.domain.device.entity.Device;
import com.example.senioron.domain.device.repository.DeviceRepository;
import com.example.senioron.domain.event.util.FcmSender;
import com.example.senioron.domain.hospital.entity.Hospital;
import com.example.senioron.domain.hospital.entity.HospitalNotificationCheckpoint;
import com.example.senioron.domain.hospital.entity.HospitalReminderType;
import com.example.senioron.domain.hospital.repository.HospitalNotificationCheckpointRepository;
import com.example.senioron.domain.hospital.repository.HospitalRepository;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class HospitalNotificationScheduler {

    private static final ZoneId KOREA_ZONE_ID =
            ZoneId.of("Asia/Seoul");

    private static final String CHECKPOINT_JOB_NAME =
            "HOSPITAL_NOTIFICATION";

    private final HospitalRepository hospitalRepository;

    private final HospitalNotificationCheckpointRepository
            checkpointRepository;

    private final DeviceRepository deviceRepository;

    private final FcmSender fcmSender;

    private final PlatformTransactionManager transactionManager;

    @Scheduled(
            cron = "0 * * * * *",
            zone = "Asia/Seoul"
    )
    public void processHospitalNotifications() {
        LocalDateTime currentMinute =
                LocalDateTime.now(KOREA_ZONE_ID)
                        .withSecond(0)
                        .withNano(0);

        executeTransaction(() ->
                processHospitalNotificationsWithLock(
                        currentMinute
                )
        );
    }

    private void processHospitalNotificationsWithLock(
            LocalDateTime currentMinute
    ) {
        checkpointRepository.insertIfAbsent(
                CHECKPOINT_JOB_NAME,
                currentMinute.minusMinutes(1)
        );

        HospitalNotificationCheckpoint checkpoint =
                checkpointRepository
                        .findByJobNameForUpdate(
                                CHECKPOINT_JOB_NAME
                        )
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "병원 알림 체크포인트를 찾을 수 없습니다."
                                )
                        );

        NotificationBatch notificationBatch =
                loadNotificationBatch(
                        currentMinute,
                        checkpoint.getLastProcessedAt()
                );

        boolean allSucceeded =
                sendNotifications(
                        notificationBatch.notifications(),
                        currentMinute
                );

        if (allSucceeded) {
            checkpoint.updateLastProcessedAt(
                    currentMinute
            );

            checkpointRepository.save(
                    checkpoint
            );
        }
    }

    private NotificationBatch loadNotificationBatch(
            LocalDateTime currentMinute,
            LocalDateTime savedCheckpoint
    ) {
        LocalDateTime fromInclusive =
                savedCheckpoint.isAfter(
                        currentMinute
                )
                        ? currentMinute.minusMinutes(1)
                        : savedCheckpoint;

        LocalDate startScheduleDate =
                fromInclusive.toLocalDate();

        LocalDate endScheduleDate =
                currentMinute
                        .plusDays(1)
                        .toLocalDate();

        List<Hospital> hospitals =
                hospitalRepository
                        .findPendingReminderCandidates(
                                HospitalReminderType.NONE,
                                startScheduleDate,
                                endScheduleDate
                        )
                        .stream()
                        .filter(hospital ->
                                isReminderDueBetween(
                                        hospital,
                                        fromInclusive,
                                        currentMinute
                                )
                        )
                        .toList();

        if (hospitals.isEmpty()) {
            return new NotificationBatch(
                    fromInclusive,
                    List.of()
            );
        }

        Map<Long, User> parentUsersById =
                new LinkedHashMap<>();

        for (Hospital hospital : hospitals) {
            User parentUser =
                    hospital.getUser();

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
                parentDevicesByUserId =
                groupDevicesByUserId(
                        parentDevices
                );

        Map<Long, List<DeviceTarget>>
                childDevicesByFamilyId =
                new LinkedHashMap<>();

        for (User parentUser
                : parentUsersById.values()) {
            if (parentUser.getFamily() == null) {
                continue;
            }

            Long familyId =
                    parentUser.getFamily()
                            .getFamilyId();

            childDevicesByFamilyId.computeIfAbsent(
                    familyId,
                    ignored ->
                            toDeviceTargets(
                                    deviceRepository
                                            .findAllByUser_FamilyAndUser_Role(
                                                    parentUser.getFamily(),
                                                    Role.CHILD
                                            )
                            )
            );
        }

        List<HospitalReminderNotification>
                notifications =
                new ArrayList<>();

        for (Hospital hospital : hospitals) {
            User parentUser =
                    hospital.getUser();

            Map<Long, DeviceTarget>
                    devicesByDeviceId =
                    new LinkedHashMap<>();

            for (DeviceTarget device :
                    parentDevicesByUserId.getOrDefault(
                            parentUser.getUsersId(),
                            List.of()
                    )) {
                devicesByDeviceId.putIfAbsent(
                        device.deviceId(),
                        device
                );
            }

            if (parentUser.getFamily() != null) {
                Long familyId =
                        parentUser.getFamily()
                                .getFamilyId();

                for (DeviceTarget device :
                        childDevicesByFamilyId.getOrDefault(
                                familyId,
                                List.of()
                        )) {
                    devicesByDeviceId.putIfAbsent(
                            device.deviceId(),
                            device
                    );
                }
            }

            notifications.add(
                    createNotification(
                            hospital,
                            new ArrayList<>(
                                    devicesByDeviceId.values()
                            )
                    )
            );
        }

        return new NotificationBatch(
                fromInclusive,
                List.copyOf(
                        notifications
                )
        );
    }

    private HospitalReminderNotification createNotification(
            Hospital hospital,
            List<DeviceTarget> devices
    ) {
        String hospitalName =
                getSafeValue(
                        hospital.getHospitalName(),
                        "병원"
                );

        String department =
                getSafeValue(
                        hospital.getDepartment(),
                        "진료"
                );

        String dayText =
                hospital.getReminderType()
                        == HospitalReminderType.DAY_BEFORE
                        ? "내일"
                        : "오늘";

        String title =
                "병원 진료 일정 알림";

        String body =
                dayText
                        + " "
                        + hospitalName
                        + " "
                        + department
                        + " 일정이 있어요.";

        Map<String, String> data =
                Map.of(
                        "type",
                        "HOSPITAL_REMINDER",

                        "title",
                        title,

                        "body",
                        body,

                        "hospitalId",
                        hospital.getHospital_id()
                                .toString(),

                        "hospitalName",
                        hospitalName,

                        "department",
                        department,

                        "scheduleDate",
                        hospital.getScheduleDate()
                                .toString(),

                        "scheduleTime",
                        hospital.getScheduleTime()
                                .toString(),

                        "reminderType",
                        hospital.getReminderType()
                                .name()
                );

        return new HospitalReminderNotification(
                hospital.getHospital_id(),
                hospital.getUser()
                        .getUsersId(),
                hospital.getScheduleDate(),
                hospital.getScheduleTime(),
                hospital.getReminderType(),
                List.copyOf(
                        devices
                ),
                data
        );
    }

    private boolean sendNotifications(
            List<HospitalReminderNotification> notifications,
            LocalDateTime processedAt
    ) {
        boolean allSucceeded =
                true;

        for (HospitalReminderNotification notification
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
                } catch (RuntimeException exception) {
                    failureCount++;

                    log.error(
                            "병원 일정 FCM 발송 요청 실패. "
                                    + "deviceId={}, hospitalId={}",
                            device.deviceId(),
                            notification.hospitalId(),
                            exception
                    );
                }
            }

            if (failureCount == 0) {
                try {
                    markReminderSent(
                            notification,
                            processedAt
                    );
                } catch (RuntimeException exception) {
                    allSucceeded = false;

                    log.error(
                            "병원 일정 알림 발송 완료 처리 실패. "
                                    + "hospitalId={}",
                            notification.hospitalId(),
                            exception
                    );
                }
            } else {
                allSucceeded = false;
            }

            log.info(
                    "병원 일정 푸시 요청 처리 종료. "
                            + "parentUserId={}, hospitalId={}, "
                            + "deviceCount={}, requestSuccessCount={}, "
                            + "requestFailureCount={}, skippedCount={}",
                    notification.parentUserId(),
                    notification.hospitalId(),
                    notification.devices().size(),
                    successCount,
                    failureCount,
                    skippedCount
            );
        }

        return allSucceeded;
    }

    private void markReminderSent(
            HospitalReminderNotification notification,
            LocalDateTime sentAt
    ) {
        int updatedCount =
                hospitalRepository.markReminderSentAt(
                        notification.hospitalId(),
                        notification.scheduleDate(),
                        notification.scheduleTime(),
                        notification.reminderType(),
                        sentAt
                );

        if (updatedCount != 1) {
            throw new IllegalStateException(
                    "병원 일정이 변경되었거나 이미 알림 처리되었습니다. "
                            + "hospitalId="
                            + notification.hospitalId()
            );
        }
    }

    private boolean isReminderDueBetween(
            Hospital hospital,
            LocalDateTime fromInclusive,
            LocalDateTime toInclusive
    ) {
        LocalDateTime reminderAt =
                calculateReminderAt(
                        hospital
                );

        if (reminderAt == null) {
            return false;
        }

        return !reminderAt.isBefore(
                fromInclusive
        ) && !reminderAt.isAfter(
                toInclusive
        );
    }

    private LocalDateTime calculateReminderAt(
            Hospital hospital
    ) {
        if (hospital.getReminderType() == null
                || hospital.getReminderType()
                == HospitalReminderType.NONE) {
            return null;
        }

        LocalDate reminderDate;

        if (hospital.getReminderType()
                == HospitalReminderType.DAY_BEFORE) {
            reminderDate =
                    hospital.getScheduleDate()
                            .minusDays(1);
        } else {
            reminderDate =
                    hospital.getScheduleDate();
        }

        return LocalDateTime.of(
                reminderDate,
                hospital.getScheduleTime()
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
                            ignored ->
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

    private List<DeviceTarget> toDeviceTargets(
            List<Device> devices
    ) {
        return devices.stream()
                .map(device ->
                        new DeviceTarget(
                                device.getDeviceId(),
                                device.getDeviceToken()
                        )
                )
                .toList();
    }

    private void executeTransaction(
            Runnable action
    ) {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(
                        transactionManager
                );

        transactionTemplate.executeWithoutResult(
                status -> action.run()
        );
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

    private record NotificationBatch(
            LocalDateTime fromInclusive,
            List<HospitalReminderNotification> notifications
    ) {
    }

    private record HospitalReminderNotification(
            Long hospitalId,
            Long parentUserId,
            LocalDate scheduleDate,
            LocalTime scheduleTime,
            HospitalReminderType reminderType,
            List<DeviceTarget> devices,
            Map<String, String> data
    ) {
    }

    private record DeviceTarget(
            Long deviceId,
            String deviceToken
    ) {
    }
}