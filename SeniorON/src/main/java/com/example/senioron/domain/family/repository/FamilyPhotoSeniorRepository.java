package com.example.senioron.domain.family.repository;

import com.example.senioron.domain.family.entity.FamilyPhotoSenior;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FamilyPhotoSeniorRepository
        extends JpaRepository<FamilyPhotoSenior, Long> {
}