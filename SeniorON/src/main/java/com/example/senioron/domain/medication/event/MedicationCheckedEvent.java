package com.example.senioron.domain.medication.event;

public record MedicationCheckedEvent(
        Long userId,
        String parentName
) {}