package com.example.senioron.domain.medication.dto.response;

import com.example.senioron.domain.medication.entity.MedicationLog;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class MedicationCheckResponse {
    private Long medicationLogId;
    private Boolean isTaken;
    private LocalDateTime takenAt;

    public static MedicationCheckResponse from(MedicationLog log) {
        return MedicationCheckResponse.builder()
                .medicationLogId(log.getMedicationLogId())
                .isTaken(log.getIsTaken())
                .takenAt(log.getTakenAt())
                .build();
    }
}