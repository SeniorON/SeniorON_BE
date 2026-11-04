package com.example.senioron.domain.senior.repository;

import com.example.senioron.domain.senior.entity.SeniorWithdrawalNoticeRecipient;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
