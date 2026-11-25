package com.example.senioron.domain.medication.repository;

import com.example.senioron.domain.medication.entity.Medication;
import com.example.senioron.domain.user.entity.User;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MedicationRepository
        extends JpaRepository<Medication, Long> {

    List<Medication>
    findAllByUserAndEffectiveToIsNullOrderByMedicineTimeAsc(
            User user
    );

    List<Medication>
    findByUserAndMedicationGroupIdAndEffectiveToIsNull(
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
              AND m.scheduleStartDate <= :targetDate
              AND (
                    m.scheduleEndDate IS NULL
                    OR m.scheduleEndDate >= :targetDate
              )
            ORDER BY m.medicineTime ASC
            """)
    List<Medication> findEffectiveMedicationsForDate(
            @Param("user")
            User user,

            @Param("dayStart")
            LocalDateTime dayStart,

            @Param("dayEndExclusive")
            LocalDateTime dayEndExclusive,

            @Param("targetDate")
            LocalDate targetDate
    );

    @Query("""
            SELECT m
            FROM Medication m
            WHERE m.user = :user
              AND m.effectiveFrom < :monthEndExclusive
              AND (
                    m.effectiveTo IS NULL
                    OR m.effectiveTo > :monthStart
              )
              AND m.scheduleStartDate < :monthEndDateExclusive
              AND (
                    m.scheduleEndDate IS NULL
                    OR m.scheduleEndDate >= :monthStartDate
              )
            ORDER BY m.medicineTime ASC
            """)
    List<Medication> findEffectiveMedicationsForMonth(
            @Param("user")
            User user,

            @Param("monthStart")
            LocalDateTime monthStart,

            @Param("monthEndExclusive")
            LocalDateTime monthEndExclusive,

            @Param("monthStartDate")
            LocalDate monthStartDate,

            @Param("monthEndDateExclusive")
            LocalDate monthEndDateExclusive
    );

    @Query("""
            SELECT m
            FROM Medication m
            WHERE m.user = :user
              AND m.effectiveFrom < :rangeEndExclusive
              AND (
                    m.effectiveTo IS NULL
                    OR m.effectiveTo > :rangeStart
              )
              AND m.scheduleStartDate < :endDateExclusive
              AND (
                    m.scheduleEndDate IS NULL
                    OR m.scheduleEndDate >= :startDate
              )
            ORDER BY m.medicineTime ASC
            """)
    List<Medication> findEffectiveMedicationsForRange(
            @Param("user")
            User user,

            @Param("rangeStart")
            LocalDateTime rangeStart,

            @Param("rangeEndExclusive")
            LocalDateTime rangeEndExclusive,

            @Param("startDate")
            LocalDate startDate,

            @Param("endDateExclusive")
            LocalDate endDateExclusive
    );

    @Query("""
            SELECT m
            FROM Medication m
            JOIN FETCH m.user u
            WHERE m.effectiveFrom < :dayEndExclusive
              AND (
                    m.effectiveTo IS NULL
                    OR m.effectiveTo > :dayStart
              )
              AND m.scheduleStartDate <= :targetDate
              AND (
                    m.scheduleEndDate IS NULL
                    OR m.scheduleEndDate >= :targetDate
              )
            ORDER BY u.usersId ASC,
                     m.medicineTime ASC
            """)
    List<Medication> findAllEffectiveMedicationsForDate(
            @Param("dayStart")
            LocalDateTime dayStart,

            @Param("dayEndExclusive")
            LocalDateTime dayEndExclusive,

            @Param("targetDate")
            LocalDate targetDate
    );
}