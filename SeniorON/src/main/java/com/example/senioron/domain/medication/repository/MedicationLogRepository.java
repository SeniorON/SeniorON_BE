package com.example.senioron.domain.medication.repository;

import com.example.senioron.domain.medication.entity.Medication;
import com.example.senioron.domain.medication.entity.MedicationLog;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface MedicationLogRepository extends JpaRepository<MedicationLog, Long> {

    @EntityGraph(attributePaths = {"medication"})
    List<MedicationLog> findByUserUsersIdAndPlannedDateOrderByPlannedTimeAsc(Long usersId, LocalDate plannedDate);

    void deleteByMedicationIn(List<Medication> medications);
}