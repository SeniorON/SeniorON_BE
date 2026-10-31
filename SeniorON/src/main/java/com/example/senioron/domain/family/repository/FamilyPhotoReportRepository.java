package com.example.senioron.domain.family.repository;

import com.example.senioron.domain.family.entity.FamilyPhotoReport;
import com.example.senioron.domain.family.entity.PhotoReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FamilyPhotoReportRepository
        extends JpaRepository<FamilyPhotoReport, Long> {

    boolean existsByFamilyPhoto_FamilyPhotoIdAndReporter_UsersId(
            Long familyPhotoId,
            Long reporterUserId
    );

    boolean existsByFamilyPhoto_FamilyPhotoIdAndStatus(
            Long familyPhotoId,
            PhotoReportStatus status
    );
}