package com.example.senioron.domain.senior.service;

import com.example.senioron.domain.senior.entity.SeniorWithdrawalNotice;
import com.example.senioron.domain.senior.repository.SeniorWithdrawalNoticeRecipientRepository;
import com.example.senioron.domain.senior.repository.SeniorWithdrawalNoticeRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SeniorWithdrawalNoticeCleanupService {

    private final SeniorWithdrawalNoticeRepository noticeRepository;
    private final SeniorWithdrawalNoticeRecipientRepository recipientRepository;

    @Value("${senior.withdrawal-notice.completion-grace-days:1}")
    private long completionGraceDays;

    @Transactional
    public int cleanupEligibleNotices() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime completedBefore = now.minusDays(completionGraceDays);
        List<Long> candidateIds = noticeRepository.findCleanupCandidateIds(
                now,
                completedBefore
        );

        int deletedCount = 0;
        for (Long noticeId : candidateIds) {
            SeniorWithdrawalNotice notice = noticeRepository
                    .findByIdForUpdate(noticeId)
                    .orElse(null);
            if (notice == null || !isCleanupEligible(notice, now, completedBefore)) {
                continue;
            }

            recipientRepository.deleteAllByNoticeId(noticeId);
            noticeRepository.delete(notice);
            deletedCount++;
        }
        return deletedCount;
    }

    private boolean isCleanupEligible(
            SeniorWithdrawalNotice notice,
            LocalDateTime now,
            LocalDateTime completedBefore
    ) {
        if (!notice.getExpiresAt().isAfter(now)) {
            return true;
        }
        return notice.getCompletedAt() != null
                && !notice.getCompletedAt().isAfter(completedBefore);
    }
}
