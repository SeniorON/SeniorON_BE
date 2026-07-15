package com.example.senioron.domain.medication.repository;

import com.example.senioron.domain.medication.entity.Medication;
import com.example.senioron.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MedicationRepository extends JpaRepository<Medication, Long> {

    List<Medication> findAllByUserOrderByMedicineTimeAsc(User user);


    @Modifying
    @Query("DELETE FROM Medication m WHERE m.user = :user AND m.medicationGroupId = :groupId")
    void deleteByUserAndMedicationGroupId(@Param("user") User user, @Param("groupId") String medicationGroupId);

    @Query("SELECT COUNT(m) > 0 FROM Medication m WHERE m.user = :user AND m.medicationGroupId = :groupId")
    boolean existsByUserAndMedicationGroupId(@Param("user") User user, @Param("groupId") String medicationGroupId);
}