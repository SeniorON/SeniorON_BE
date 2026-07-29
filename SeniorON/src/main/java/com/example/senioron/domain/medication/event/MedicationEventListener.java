package com.example.senioron.domain.medication.event;

import com.example.senioron.domain.device.entity.Device;
import com.example.senioron.domain.device.repository.DeviceRepository;
import com.example.senioron.domain.event.util.FcmSender;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class MedicationEventListener {

    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final FcmSender fcmSender;

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            readOnly = true
    )
    public void handleMedicationChecked(
            MedicationCheckedEvent event
    ) {
        User parentUser = userRepository
                .findById(event.parentUserId())
                .orElse(null);

        if (parentUser == null
                || parentUser.getFamily() == null) {
            log.warn(
                    "복약 완료 푸시 대상 가족을 찾을 수 없습니다. parentUserId={}",
                    event.parentUserId()
            );
            return;
        }

        List<User> childUsers =
                userRepository.findByFamilyAndUsersIdNotAndRole(
                        parentUser.getFamily(),
                        parentUser.getUsersId(),
                        Role.CHILD
                );

        if (childUsers.isEmpty()) {
            log.warn(
                    "복약 완료 푸시를 받을 자녀 사용자가 없습니다. parentUserId={}",
                    event.parentUserId()
            );
            return;
        }

        List<Device> childDevices =
                deviceRepository.findAllByUserIn(
                        childUsers
                );

        if (childDevices.isEmpty()) {
            log.warn(
                    "복약 완료 푸시를 받을 자녀 기기가 없습니다. parentUserId={}",
                    event.parentUserId()
            );
            return;
        }

        String parentName = getSafeValue(
                event.parentName(),
                "부모님"
        );

        String medicineName = getSafeValue(
                event.medicineName(),
                "약"
        );

        String title = "복약 완료";

        String body = parentName
                + "님이 "
                + medicineName
                + "을(를) 복용했어요.";

        Map<String, String> data = Map.of(
                "type", "MEDICATION_CHECKED",
                "title", title,
                "body", body,
                "parentUserId", String.valueOf(event.parentUserId()),
                "medicationLogId", String.valueOf(event.medicationLogId()),
                "medicineName", medicineName
        );

        int successCount = 0;
        int failureCount = 0;
        int skippedCount = 0;

        for (Device childDevice : childDevices) {
            String deviceToken =
                    childDevice.getDeviceToken();

            if (deviceToken == null
                    || deviceToken.isBlank()) {
                skippedCount++;

                log.warn(
                        "자녀 기기의 FCM 토큰이 없습니다. deviceId={}",
                        childDevice.getDeviceId()
                );

                continue;
            }

            try {
                fcmSender.sendData(
                        deviceToken,
                        data
                );

                successCount++;

                log.info(
                        "자녀 복약 완료 FCM 발송 요청 처리. deviceId={}, medicationLogId={}",
                        childDevice.getDeviceId(),
                        event.medicationLogId()
                );
            } catch (RuntimeException e) {
                failureCount++;

                log.error(
                        "자녀 복약 완료 FCM 발송 요청 실패. deviceId={}, medicationLogId={}",
                        childDevice.getDeviceId(),
                        event.medicationLogId(),
                        e
                );
            }
        }

        log.info(
                "자녀 복약 완료 푸시 요청 처리 종료. parentUserId={}, childDeviceCount={}, "
                        + "requestSuccessCount={}, requestFailureCount={}, skippedCount={}",
                event.parentUserId(),
                childDevices.size(),
                successCount,
                failureCount,
                skippedCount
        );
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