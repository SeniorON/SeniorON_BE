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
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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

    @Scheduled(
            cron = "0 * * * * *",
            zone = "Asia/Seoul"
    )
    public void processMedicationNotifications() {
        LocalDateTime currentMinute =
                LocalDateTime.now(KOREA_ZONE_ID)
                        .withSecond(0)
                        .withNano(0);

        sendParentMedicationReminders(
                currentMinute
        );

        LocalDateTime missedMedicationTime =
                currentMinute.minusMinutes(
                        MISSED_DELAY_MINUTES
                );

        sendChildMissedNotifications(
                missedMedicationTime
        );
    }

    private void sendParentMedicationReminders(
            LocalDateTime targetDateTime
    ) {
        List<MedicationLog> medicationLogs =
                medicationLogRepository
                        .findByPlannedDateAndPlannedTimeAndIsTakenFalseOrderByMedicationLogIdAsc(
                                targetDateTime.toLocalDate(),
                                targetDateTime.toLocalTime()
                        );

        for (MedicationLog medicationLog : medicationLogs) {
            User parentUser =
                    medicationLog.getUser();

            List<Device> parentDevices =
                    deviceRepository.findAllByUserIn(
                            List.of(parentUser)
                    );

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

            for (Device parentDevice : parentDevices) {
                String deviceToken =
                        parentDevice.getDeviceToken();

                if (deviceToken == null
                        || deviceToken.isBlank()) {
                    continue;
                }

                fcmSender.sendData(
                        deviceToken,
                        data
                );
            }

            log.info(
                    "부모 복약 시간 푸시 처리 완료. parentUserId={}, medicationLogId={}, deviceCount={}",
                    parentUser.getUsersId(),
                    medicationLog.getMedicationLogId(),
                    parentDevices.size()
            );
        }
    }

    private void sendChildMissedNotifications(
            LocalDateTime targetDateTime
    ) {
        List<MedicationLog> medicationLogs =
                medicationLogRepository
                        .findByPlannedDateAndPlannedTimeAndIsTakenFalseOrderByMedicationLogIdAsc(
                                targetDateTime.toLocalDate(),
                                targetDateTime.toLocalTime()
                        );

        for (MedicationLog medicationLog : medicationLogs) {
            boolean isStillUntaken =
                    medicationLogRepository
                            .existsByMedicationLogIdAndIsTakenFalse(
                                    medicationLog.getMedicationLogId()
                            );

            if (!isStillUntaken) {
                continue;
            }

            User parentUser =
                    medicationLog.getUser();

            if (parentUser.getFamily() == null) {
                continue;
            }

            List<User> childUsers =
                    userRepository
                            .findByFamilyAndUsersIdNotAndRole(
                                    parentUser.getFamily(),
                                    parentUser.getUsersId(),
                                    Role.CHILD
                            );

            if (childUsers.isEmpty()) {
                continue;
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

            List<Device> childDevices =
                    deviceRepository.findAllByUserIn(
                            childUsers
                    );

            for (Device childDevice : childDevices) {
                String deviceToken =
                        childDevice.getDeviceToken();

                if (deviceToken == null
                        || deviceToken.isBlank()) {
                    continue;
                }

                fcmSender.send(
                        deviceToken,
                        title,
                        body
                );
            }

            log.info(
                    "자녀 미복용 푸시 처리 완료. parentUserId={}, medicationLogId={}, childDeviceCount={}",
                    parentUser.getUsersId(),
                    medicationLog.getMedicationLogId(),
                    childDevices.size()
            );
        }
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
}