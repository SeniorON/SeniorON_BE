package com.example.senioron.domain.family.repository;

import com.example.senioron.domain.family.entity.Family;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FamilyRepository extends JpaRepository<Family, Long> {

    boolean existsByFamilyCode(String familyCode);

    Optional<Family> findByFamilyCode(String familyCode);

}