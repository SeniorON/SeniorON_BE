package com.example.senioron.domain.notification.scheduler;

import com.example.senioron.domain.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationCleanupScheduler {

    private final NotificationService notificationService;

    //매일 새벽 4시
    @Scheduled(cron = "0 0 4 * * *")
    public void cleanupOldNotifications() {
        notificationService.deleteOldNotifications();
    }
}
