package com.example.senioron.domain.senior.scheduler;

import com.example.senioron.domain.senior.service.SeniorWithdrawalNoticeCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeniorWithdrawalNoticeCleanupScheduler {

    private final SeniorWithdrawalNoticeCleanupService cleanupService;

    @Scheduled(cron = "${senior.withdrawal-notice.cleanup-cron:0 30 4 * * *}")
    public void cleanupWithdrawalNotices() {
        int deletedCount = cleanupService.cleanupEligibleNotices();
        log.info("시니어 탈퇴 알림 정리 완료: {}건 삭제", deletedCount);
    }
}
