package com.example.senioron.domain.senior.repository;

import com.example.senioron.domain.senior.entity.SeniorWithdrawalNoticeRecipient;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeniorWithdrawalNoticeRecipientRepository
        extends JpaRepository<SeniorWithdrawalNoticeRecipient, Long> {

    @Query("""
            SELECT recipient
            FROM SeniorWithdrawalNoticeRecipient recipient
            JOIN FETCH recipient.notice notice
            WHERE recipient.recipientUser.usersId = :recipientUserId
              AND recipient.acknowledgedAt IS NULL
              AND notice.expiresAt > :now
            ORDER BY notice.withdrawnAt DESC,
                     notice.seniorWithdrawalNoticeId DESC
            """)
    List<SeniorWithdrawalNoticeRecipient> findUnacknowledgedAndUnexpiredByRecipientUserId(
            @Param("recipientUserId") Long recipientUserId,
            @Param("now") LocalDateTime now
    );

    @Query("""
            SELECT recipient
            FROM SeniorWithdrawalNoticeRecipient recipient
            WHERE recipient.notice.seniorWithdrawalNoticeId = :noticeId
              AND recipient.recipientUser.usersId = :recipientUserId
            """)
    Optional<SeniorWithdrawalNoticeRecipient> findByNoticeIdAndRecipientUserId(
            @Param("noticeId") Long noticeId,
            @Param("recipientUserId") Long recipientUserId
    );

    boolean existsByNoticeSeniorWithdrawalNoticeIdAndAcknowledgedAtIsNull(
            Long noticeId
    );

    @Modifying
    @Query("""
            DELETE FROM SeniorWithdrawalNoticeRecipient recipient
            WHERE recipient.notice.seniorWithdrawalNoticeId = :noticeId
            """)
    int deleteAllByNoticeId(@Param("noticeId") Long noticeId);
}
