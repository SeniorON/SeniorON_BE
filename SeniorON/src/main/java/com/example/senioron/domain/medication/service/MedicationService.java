package com.example.senioron.domain.medication.service;

import com.example.senioron.domain.medication.dto.request.MedicationCreateRequest;
import com.example.senioron.domain.medication.dto.response.MedicationCreateResponse;
import com.example.senioron.domain.medication.entity.Medication;
import com.example.senioron.domain.medication.repository.MedicationRepository;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MedicationService {

    private final MedicationRepository medicationRepository;
    private final UserRepository userRepository;

    @Transactional
    public MedicationCreateResponse createMedication(Long usersId, MedicationCreateRequest request) {
        User user = userRepository.findById(usersId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        String medicineDays = String.join(",", request.getMedicineDays());

        List<Long> medicationIds = new ArrayList<>();
        List<String> medicineTimes = new ArrayList<>();

        for (LocalTime medicineTime : request.getMedicineTimes()) {
            Medication medication = Medication.builder()
                    .user(user)
                    .medicineName(request.getMedicineName())
                    .ingredientName(request.getIngredientName())
                    .medicineTime(medicineTime)
                    .medicineDays(medicineDays)
                    .build();

            Medication savedMedication = medicationRepository.save(medication);

            medicationIds.add(savedMedication.getMedication_id());
            medicineTimes.add(savedMedication.getMedicineTime().toString());
        }

        return new MedicationCreateResponse(
                medicationIds,
                request.getMedicineName(),
                request.getIngredientName(),
                medicineTimes,
                request.getMedicineDays()
        );
    }
}