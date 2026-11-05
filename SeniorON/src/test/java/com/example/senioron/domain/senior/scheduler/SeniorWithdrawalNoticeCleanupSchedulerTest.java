package com.example.senioron.domain.senior.scheduler;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.senioron.domain.senior.service.SeniorWithdrawalNoticeCleanupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SeniorWithdrawalNoticeCleanupSchedulerTest {

    @Mock
    private SeniorWithdrawalNoticeCleanupService cleanupService;

    @InjectMocks
    private SeniorWithdrawalNoticeCleanupScheduler scheduler;

    @Test
    void delegatesCleanupToWithdrawalNoticeCleanupService() {
        when(cleanupService.cleanupEligibleNotices()).thenReturn(2);

        scheduler.cleanupWithdrawalNotices();

        verify(cleanupService).cleanupEligibleNotices();
    }
}
