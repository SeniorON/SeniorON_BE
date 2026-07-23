package com.example.senioron.domain.medication.service;

import com.example.senioron.domain.medication.dto.response.MedicationCheckResponse;
import com.example.senioron.domain.medication.dto.response.MedicationScheduleResponse;
import com.example.senioron.domain.medication.entity.MedicationLog;
import com.example.senioron.domain.medication.event.MedicationCheckedEvent;
import com.example.senioron.domain.medication.repository.MedicationLogRepository;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MedicationLogService {

    private final MedicationLogRepository medicationLogRepository;
    private final ApplicationEventPublisher eventPublisher;

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
    public MedicationCheckResponse checkMedication(Long medicationLogId, User user) {
        MedicationLog logEntity = medicationLogRepository.findById(medicationLogId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDICATION_NOT_FOUND));

        if (!logEntity.getUser().getUsersId().equals(user.getUsersId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if (Boolean.TRUE.equals(logEntity.getIsTaken())) {
            return MedicationCheckResponse.from(logEntity);
        }

        LocalDateTime now = LocalDateTime.now();
        int updatedRows = medicationLogRepository.markAsTakenIfUntaken(medicationLogId, now);

        if (updatedRows > 0) {
            eventPublisher.publishEvent(new MedicationCheckedEvent(
                    user.getUsersId(),
                    logEntity.getUser().getName()
            ));
        }

        MedicationLog updatedLog = medicationLogRepository.findById(medicationLogId)
                .orElse(logEntity);

        return MedicationCheckResponse.from(updatedLog);
    }
}