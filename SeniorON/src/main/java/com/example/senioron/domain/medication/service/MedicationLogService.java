package com.example.senioron.domain.medication.service;

import com.example.senioron.domain.medication.dto.response.MedicationScheduleResponse;
import com.example.senioron.domain.medication.entity.MedicationLog;
import com.example.senioron.domain.medication.repository.MedicationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MedicationLogService {

    private final MedicationLogRepository medicationLogRepository;

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
}