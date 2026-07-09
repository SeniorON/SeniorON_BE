package com.example.senioron.domain.medication.repository;

import com.example.senioron.domain.medication.entity.Medication;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicationRepository extends JpaRepository<Medication, Long> {
}