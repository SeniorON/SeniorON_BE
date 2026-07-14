package com.example.senioron.domain.medication.dto.response;


import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.List;

@Getter
@AllArgsConstructor
public class MedicationCreateResponse {

    private List<Long> medicationIds;

    private String medicationGroupId;

    private String medicineName;

    private String ingredientName;

    private List<String> medicineTimes;

    private List<String> medicineDays;
}
