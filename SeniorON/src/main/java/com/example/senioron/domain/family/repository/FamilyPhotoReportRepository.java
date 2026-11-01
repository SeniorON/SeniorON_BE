package com.example.senioron.domain.family.repository;

import com.example.senioron.domain.family.entity.FamilyPhotoReport;
import com.example.senioron.domain.family.entity.PhotoReportStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FamilyPhotoReportRepository
        extends JpaRepository<FamilyPhotoReport, Long> {

    boolean existsByFamilyPhoto_FamilyPhotoIdAndReporter_UsersId(
            Long familyPhotoId,
            Long reporterUserId
    );

    // 엔티티를 영속성 컨텍스트에 올리지 않도록 ID만 잠금 조회한다.
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
        SELECT fpr.id
        FROM FamilyPhotoReport fpr
        WHERE fpr.imageKey = :imageKey
          AND fpr.status = :status
        """)
    List<Long> findIdsByImageKeyAndStatus(
            @Param("imageKey") String imageKey,
            @Param("status") PhotoReportStatus status
    );
}
