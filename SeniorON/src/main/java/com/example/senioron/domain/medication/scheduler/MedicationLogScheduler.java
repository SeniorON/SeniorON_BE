package com.example.senioron.domain.medication.scheduler;

import com.example.senioron.domain.medication.service.MedicationLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MedicationLogScheduler {

    private final MedicationLogService medicationLogService;

    @EventListener(ApplicationReadyEvent.class)
    public void createMedicationLogsOnStartup() {
        createTodayMedicationLogs();
    }

    @Scheduled(
            cron = "0 0 0 * * *",
            zone = "Asia/Seoul"
    )
    public void createMedicationLogsEveryDay() {
        createTodayMedicationLogs();
    }

    private void createTodayMedicationLogs() {
        medicationLogService
                .createTodayMedicationLogsForAllMedicationOwners();

        log.info("오늘 복약 로그 생성 완료");
    }
}