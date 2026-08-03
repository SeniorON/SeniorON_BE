package com.example.senioron.domain.hospital.scheduler;

import com.example.senioron.domain.device.entity.Device;
import com.example.senioron.domain.device.repository.DeviceRepository;
import com.example.senioron.domain.event.util.FcmSender;
import com.example.senioron.domain.hospital.entity.Hospital;
import com.example.senioron.domain.hospital.entity.HospitalReminderType;
import com.example.senioron.domain.hospital.repository.HospitalRepository;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
public class HospitalNotificationScheduler {

    private static final ZoneId KOREA_ZONE_ID =
            ZoneId.of("Asia/Seoul");

    private final HospitalRepository hospitalRepository;
    private final DeviceRepository deviceRepository;
    private final FcmSender fcmSender;
    private final PlatformTransactionManager transactionManager;

    @Scheduled(
            cron = "0 * * * * *",
            zone = "Asia/Seoul"
    )
    public void processHospitalNotifications() {
        LocalDateTime currentMinute =
                LocalDateTime.now(
                                KOREA_ZONE_ID
                        )
                        .withSecond(0)
                        .withNano(0);

        List<HospitalReminderNotification> notifications =
                loadHospitalReminderNotifications(
                        currentMinute
                );

        sendHospitalReminderNotifications(
                notifications
        );
    }

    private List<HospitalReminderNotification>
    loadHospitalReminderNotifications(
            LocalDateTime currentMinute
    ) {
        return executeReadOnlyTransaction(() -> {
            List<Hospital> hospitals =
                    hospitalRepository
                            .findHospitalReminderTargets(
                                    currentMinute.toLocalDate(),
                                    currentMinute.toLocalDate()
                                            .plusDays(1),
                                    currentMinute.toLocalTime(),
                                    HospitalReminderType.SAME_DAY,
                                    HospitalReminderType.DAY_BEFORE
                            );

            if (hospitals.isEmpty()) {
                return List.of();
            }

            List<HospitalReminderNotification>
                    notifications =
                    new ArrayList<>();

            for (Hospital hospital : hospitals) {
                User parentUser =
                        hospital.getUser();

                List<DeviceTarget> targetDevices =
                        collectTargetDevices(
                                parentUser
                        );

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

                String formattedTime =
                        formatTime(
                                hospital.getScheduleTime()
                        );

                String title =
                        createNotificationTitle(
                                hospital.getReminderType()
                        );

                String body =
                        createNotificationBody(
                                hospital.getReminderType(),
                                formattedTime,
                                hospitalName,
                                department
                        );

                Map<String, String> data =
                        Map.ofEntries(
                                Map.entry(
                                        "type",
                                        "HOSPITAL_REMINDER"
                                ),
                                Map.entry(
                                        "title",
                                        title
                                ),
                                Map.entry(
                                        "body",
                                        body
                                ),
                                Map.entry(
                                        "hospitalId",
                                        String.valueOf(
                                                hospital.getHospital_id()
                                        )
                                ),
                                Map.entry(
                                        "parentUserId",
                                        String.valueOf(
                                                parentUser.getUsersId()
                                        )
                                ),
                                Map.entry(
                                        "hospitalName",
                                        hospitalName
                                ),
                                Map.entry(
                                        "department",
                                        department
                                ),
                                Map.entry(
                                        "scheduleDate",
                                        hospital.getScheduleDate()
                                                .toString()
                                ),
                                Map.entry(
                                        "scheduleTime",
                                        hospital.getScheduleTime()
                                                .toString()
                                ),
                                Map.entry(
                                        "reminderType",
                                        hospital.getReminderType()
                                                .name()
                                )
                        );

                notifications.add(
                        new HospitalReminderNotification(
                                hospital.getHospital_id(),
                                parentUser.getUsersId(),
                                hospital.getReminderType(),
                                List.copyOf(
                                        targetDevices
                                ),
                                data
                        )
                );
            }

            return notifications;
        });
    }

    private List<DeviceTarget> collectTargetDevices(
            User parentUser
    ) {
        Map<Long, DeviceTarget> uniqueDevices =
                new LinkedHashMap<>();

        List<Device> parentDevices =
                deviceRepository.findAllByUser(
                        parentUser
                );

        addDeviceTargets(
                uniqueDevices,
                parentDevices
        );

        if (parentUser.getFamily() != null) {
            List<Device> childDevices =
                    deviceRepository
                            .findAllByUser_FamilyAndUser_Role(
                                    parentUser.getFamily(),
                                    Role.CHILD
                            );

            addDeviceTargets(
                    uniqueDevices,
                    childDevices
            );
        }

        return new ArrayList<>(
                uniqueDevices.values()
        );
    }

    private void addDeviceTargets(
            Map<Long, DeviceTarget> uniqueDevices,
            List<Device> devices
    ) {
        for (Device device : devices) {
            uniqueDevices.putIfAbsent(
                    device.getDeviceId(),
                    new DeviceTarget(
                            device.getDeviceId(),
                            device.getDeviceToken()
                    )
            );
        }
    }

    private void sendHospitalReminderNotifications(
            List<HospitalReminderNotification> notifications
    ) {
        for (HospitalReminderNotification notification
                : notifications) {
            int successCount = 0;
            int failureCount = 0;
            int skippedCount = 0;

            for (DeviceTarget device :
                    notification.devices()) {
                String deviceToken =
                        device.deviceToken();

                if (deviceToken == null
                        || deviceToken.isBlank()) {
                    skippedCount++;

                    log.warn(
                            "진료 알림 대상 기기의 FCM 토큰이 없습니다. "
                                    + "deviceId={}, hospitalId={}",
                            device.deviceId(),
                            notification.hospitalId()
                    );

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
                            "진료 일정 FCM 발송 요청 실패. "
                                    + "deviceId={}, hospitalId={}",
                            device.deviceId(),
                            notification.hospitalId(),
                            exception
                    );
                }
            }

            log.info(
                    "진료 일정 푸시 요청 처리 종료. "
                            + "parentUserId={}, hospitalId={}, reminderType={}, "
                            + "deviceCount={}, requestSuccessCount={}, "
                            + "requestFailureCount={}, skippedCount={}",
                    notification.parentUserId(),
                    notification.hospitalId(),
                    notification.reminderType(),
                    notification.devices().size(),
                    successCount,
                    failureCount,
                    skippedCount
            );
        }
    }

    private String createNotificationTitle(
            HospitalReminderType reminderType
    ) {
        return switch (reminderType) {
            case DAY_BEFORE ->
                    "내일 진료 일정이 있어요";
            case SAME_DAY ->
                    "진료 시간이에요";
            case NONE ->
                    "진료 일정 알림";
        };
    }

    private String createNotificationBody(
            HospitalReminderType reminderType,
            String formattedTime,
            String hospitalName,
            String department
    ) {
        String dayText =
                switch (reminderType) {
                    case DAY_BEFORE ->
                            "내일";
                    case SAME_DAY ->
                            "오늘";
                    case NONE ->
                            "";
                };

        return dayText
                + " "
                + formattedTime
                + "에 "
                + hospitalName
                + " "
                + department
                + " 진료가 예정되어 있어요.";
    }

    private String formatTime(
            LocalTime time
    ) {
        int hour =
                time.getHour();

        int minute =
                time.getMinute();

        String period =
                hour < 12
                        ? "오전"
                        : "오후";

        int displayHour =
                hour % 12;

        if (displayHour == 0) {
            displayHour = 12;
        }

        if (minute == 0) {
            return period
                    + " "
                    + displayHour
                    + "시";
        }

        return String.format(
                "%s %d시 %02d분",
                period,
                displayHour,
                minute
        );
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

    private record HospitalReminderNotification(
            Long hospitalId,
            Long parentUserId,
            HospitalReminderType reminderType,
            List<DeviceTarget> devices,
            Map<String, String> data
    ) {
    }
}