package com.example.senioron.domain.medication.repository;

import com.example.senioron.domain.medication.entity.Medication;
import com.example.senioron.domain.user.entity.User;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MedicationRepository extends JpaRepository<Medication, Long> {

    List<Medication> findAllByUserAndEffectiveToIsNullOrderByMedicineTimeAsc(
            User user
    );

    List<Medication> findByUserAndMedicationGroupIdAndEffectiveToIsNull(
            User user,
            String medicationGroupId
    );

    @Query("""
            SELECT m
            FROM Medication m
            WHERE m.user = :user
              AND m.effectiveFrom < :dayEndExclusive
              AND (
                    m.effectiveTo IS NULL
                    OR m.effectiveTo > :dayStart
              )
            ORDER BY m.medicineTime ASC
            """)
    List<Medication> findEffectiveMedicationsForDate(
            @Param("user") User user,
            @Param("dayStart") LocalDateTime dayStart,
            @Param("dayEndExclusive") LocalDateTime dayEndExclusive
    );
}