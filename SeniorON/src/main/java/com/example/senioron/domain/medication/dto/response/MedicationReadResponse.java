package com.example.senioron.domain.medication.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MedicationReadResponse {

    private Long medicationId;
    private String medicineName;
    private String ingredientName;
    private String medicineTime;
    private String medicineDays;
}
