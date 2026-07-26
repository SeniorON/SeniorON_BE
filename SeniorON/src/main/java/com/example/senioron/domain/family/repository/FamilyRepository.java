package com.example.senioron.domain.family.repository;

import com.example.senioron.domain.family.entity.Family;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FamilyRepository extends JpaRepository<Family, Long> {

    boolean existsByFamilyCode(String familyCode);

    Optional<Family> findByFamilyCode(String familyCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM Family f WHERE f.familyId = :familyId")
    Optional<Family> findByIdForUpdate(@Param("familyId") Long familyId);

}
