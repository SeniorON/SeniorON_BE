package com.example.senioron.domain.user.scheduler;

import com.example.senioron.domain.user.repository.RefreshTokenRepository;
import com.example.senioron.domain.admin.repository.AdminRefreshTokenRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupScheduler {

    private final RefreshTokenRepository refreshTokenRepository;
    private final AdminRefreshTokenRepository adminRefreshTokenRepository;

    @Transactional
    @Scheduled(cron = "0 0 3 * * *")
    public void deleteExpiredRefreshTokens() {
        LocalDateTime now = LocalDateTime.now();
        refreshTokenRepository.deleteByExpiresAtBefore(now);
        adminRefreshTokenRepository.deleteByExpiresAtBeforeOrRevokedTrue(now);
    }
}
