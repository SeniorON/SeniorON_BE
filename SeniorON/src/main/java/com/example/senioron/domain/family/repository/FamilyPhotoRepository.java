package com.example.senioron.domain.family.repository;

import com.example.senioron.domain.family.entity.FamilyPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FamilyPhotoRepository extends JpaRepository<FamilyPhoto, Long> {
}
