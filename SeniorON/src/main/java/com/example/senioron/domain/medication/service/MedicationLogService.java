package com.example.senioron.domain.medication.service;

import com.example.senioron.domain.device.entity.Device;
import com.example.senioron.domain.device.repository.DeviceRepository;
import com.example.senioron.domain.event.util.FcmSender;
import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.medication.dto.response.MedicationCheckResponse;
import com.example.senioron.domain.medication.dto.response.MedicationScheduleResponse;
import com.example.senioron.domain.medication.entity.MedicationLog;
import com.example.senioron.domain.medication.repository.MedicationLogRepository;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MedicationLogService {

    private final MedicationLogRepository medicationLogRepository;
    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final FcmSender fcmSender;

    public List<MedicationScheduleResponse> getDailyMedicationSchedules(Long userId, LocalDate date) {
        List<MedicationLog> schedules = medicationLogRepository.findByUserUsersIdAndPlannedDateOrderByPlannedTimeAsc(userId, date);

        return schedules.stream()
                .map(log -> MedicationScheduleResponse.builder()
                        .medicationLogId(log.getMedicationLogId())
                        .medicineName(log.getMedication().getMedicineName())
                        .plannedTime(log.getPlannedTime())
                        .isTaken(log.getIsTaken())
                        .build())
                .collect(Collectors.toList());
    }


    @Transactional
    public MedicationCheckResponse checkMedication(Long medicationLogId) {
        MedicationLog logEntity = medicationLogRepository.findById(medicationLogId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDICATION_NOT_FOUND));

        logEntity.markAsTaken();

        User parent = logEntity.getUser();
        Family family = parent.getFamily();

        if (family != null) {
            List<User> children = userRepository.findAllByFamily(family).stream()
                    .filter(user -> user.getRole() == Role.CHILD)
                    .collect(Collectors.toList());

            if (!children.isEmpty()) {
                List<Device> devices = deviceRepository.findAllByUserIn(children);

                for (Device device : devices) {
                    String token = device.getDeviceToken();
                    if (token != null && !token.isBlank()) {

                        log.info("📢 [FCM 전송 시도] 수신자: {}, 디바이스 토큰: {}", parent.getName(), token);

                        fcmSender.send(
                                token,
                                "복약 알림",
                                parent.getName() + "님이 약을 복용하셨습니다."
                        );

                        //전송 확인 로그
                        log.info(" [FCM 전송 완료] fcmSender.send() 성공적으로 호출됨!");
                    } else {
                        log.warn(" [FCM 전송 불가] 해당 유저의 디바이스 토큰이 비어있습니다.");
                    }
                }
            } else {
                log.info(" [FCM 미발송] 알림을 받을 자식 계정(Role.CHILD)이 존재하지 않습니다.");
            }
        } else {
            log.info(" [FCM 미발송] 속한 패밀리(Family)가 없습니다.");
        }

        return MedicationCheckResponse.from(logEntity);
    }
}