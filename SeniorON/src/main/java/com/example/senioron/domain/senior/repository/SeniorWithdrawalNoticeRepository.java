package com.example.senioron.domain.senior.repository;

import com.example.senioron.domain.senior.entity.SeniorWithdrawalNotice;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeniorWithdrawalNoticeRepository
        extends JpaRepository<SeniorWithdrawalNotice, Long> {

    Optional<SeniorWithdrawalNotice> findBySeniorId(Long seniorId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT notice
            FROM SeniorWithdrawalNotice notice
            WHERE notice.seniorWithdrawalNoticeId = :noticeId
            """)
    Optional<SeniorWithdrawalNotice> findByIdForUpdate(
            @Param("noticeId") Long noticeId
    );

    @Query("""
            SELECT notice.seniorWithdrawalNoticeId
            FROM SeniorWithdrawalNotice notice
            WHERE notice.expiresAt <= :now
               OR (
                    notice.completedAt IS NOT NULL
                    AND notice.completedAt <= :completedBefore
               )
            ORDER BY notice.seniorWithdrawalNoticeId ASC
            """)
    List<Long> findCleanupCandidateIds(
            @Param("now") LocalDateTime now,
            @Param("completedBefore") LocalDateTime completedBefore
    );
}
