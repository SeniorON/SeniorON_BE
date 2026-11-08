package com.example.senioron.domain.family.repository;

import com.example.senioron.domain.family.entity.FamilyUserReport;
import com.example.senioron.domain.family.entity.PhotoReportStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FamilyUserReportRepository
        extends JpaRepository<FamilyUserReport, Long> {

    boolean existsByReportedUser_UsersIdAndReporter_UsersId(
            Long reportedUserId,
            Long reporterUserId
    );

    // 관리자 상세 조회: 화면에 필요한 연관 엔티티를 한 번에 조회한다.
    @EntityGraph(attributePaths = {
            "reporter",
            "reportedUser",
            "senior",
            "senior.family"
    })
    Optional<FamilyUserReport> findDetailById(Long id);

    // 엔티티를 영속성 컨텍스트에 올리지 않도록 ID만 잠금 조회한다.
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
        SELECT fur.id
        FROM FamilyUserReport fur
        WHERE fur.imageKey = :imageKey
          AND fur.status = :status
        """)
    List<Long> findIdsByImageKeyAndStatus(
            @Param("imageKey") String imageKey,
            @Param("status") PhotoReportStatus status
    );
}