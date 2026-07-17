package com.example.senioron.domain.medication.repository;

import com.example.senioron.domain.medication.entity.MedicationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface MedicationLogRepository extends JpaRepository<MedicationLog, Long> {

    List<MedicationLog> findByUserUsersIdAndPlannedDateOrderByPlannedTimeAsc(Long usersId, LocalDate plannedDate);
}