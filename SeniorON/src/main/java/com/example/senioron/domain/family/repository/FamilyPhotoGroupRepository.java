package com.example.senioron.domain.family.repository;

import com.example.senioron.domain.family.entity.FamilyPhotoGroup;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FamilyPhotoGroupRepository
        extends JpaRepository<FamilyPhotoGroup, Long> {
}