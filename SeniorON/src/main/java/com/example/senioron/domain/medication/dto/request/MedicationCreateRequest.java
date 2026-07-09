package com.example.senioron.domain.medication.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.List;

@Getter
@NoArgsConstructor
public class MedicationCreateRequest {

    @NotBlank
    private String medicineName;

    private String ingredientName;

    @NotEmpty
    private List<LocalTime> medicineTimes;

    @NotEmpty
    private List<String> medicineDays;
}