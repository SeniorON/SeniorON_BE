package com.example.senioron.domain.medication.event;

public record MedicationCheckedEvent(
        Long parentUserId,
        String parentName,
        Long medicationLogId,
        String medicineName
) {
}