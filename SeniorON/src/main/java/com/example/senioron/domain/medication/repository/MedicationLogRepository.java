package com.example.senioron.domain.medication.repository;

import com.example.senioron.domain.medication.entity.Medication;
import com.example.senioron.domain.medication.entity.MedicationLog;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MedicationLogRepository
        extends JpaRepository<MedicationLog, Long> {

    @EntityGraph(attributePaths = "medication")
    List<MedicationLog>
    findByUserUsersIdAndPlannedDateOrderByPlannedTimeAsc(
            Long userId,
            LocalDate plannedDate
    );

    @EntityGraph(attributePaths = "medication")
    List<MedicationLog>
    findByUserUsersIdAndPlannedDateAndIsTakenFalseOrderByPlannedTimeAsc(
            Long userId,
            LocalDate plannedDate
    );

    @EntityGraph(
            attributePaths = {
                    "user",
                    "user.family",
                    "medication"
            }
    )
    List<MedicationLog>
    findByPlannedDateAndPlannedTimeAndIsTakenFalseOrderByMedicationLogIdAsc(
            LocalDate plannedDate,
            LocalTime plannedTime
    );

    boolean existsByMedicationLogIdAndIsTakenFalse(
            Long medicationLogId
    );

    @Modifying(
            clearAutomatically = true,
            flushAutomatically = true
    )
    @Query("""
            UPDATE MedicationLog medicationLog
            SET medicationLog.isTaken = true,
                medicationLog.takenAt = :takenAt
            WHERE medicationLog.medicationLogId = :medicationLogId
              AND medicationLog.isTaken = false
            """)
    int markAsTakenIfUntaken(
            @Param("medicationLogId")
            Long medicationLogId,

            @Param("takenAt")
            LocalDateTime takenAt
    );

    @Modifying(
            clearAutomatically = true,
            flushAutomatically = true
    )
    @Query("""
            DELETE FROM MedicationLog medicationLog
            WHERE medicationLog.medication IN :medications
              AND medicationLog.isTaken = false
              AND (
                    medicationLog.plannedDate > :today
                    OR (
                        medicationLog.plannedDate = :today
                        AND medicationLog.plannedTime >= :currentTime
                    )
              )
            """)
    int deleteFutureUntakenLogs(
            @Param("medications")
            List<Medication> medications,

            @Param("today")
            LocalDate today,

            @Param("currentTime")
            LocalTime currentTime
    );
}