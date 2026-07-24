package com.example.senioron.domain.medication.repository;

import com.example.senioron.domain.medication.entity.Medication;
import com.example.senioron.domain.medication.entity.MedicationLog;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface MedicationLogRepository extends JpaRepository<MedicationLog, Long> {

    @EntityGraph(attributePaths = "medication")
    List<MedicationLog> findByUserUsersIdAndPlannedDateOrderByPlannedTimeAsc(Long userId, LocalDate plannedDate);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE MedicationLog m SET m.isTaken = true, m.takenAt = :takenAt " +
            "WHERE m.medicationLogId = :medicationLogId AND m.isTaken = false")
    int markAsTakenIfUntaken(@Param("medicationLogId") Long medicationLogId, @Param("takenAt") LocalDateTime takenAt);

    void deleteByMedicationIn(List<Medication> medications);
}