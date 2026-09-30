package com.example.senioron.domain.home.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class HomeWebSocketService {

    private final SimpMessagingTemplate messagingTemplate;

    public void notifyHomeUpdated(Long seniorId) {

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            sendHomeUpdated(seniorId);
                        }
                    }
            );

            return;
        }

        sendHomeUpdated(seniorId);
    }

    public void notifyScheduleUpdated(Long seniorId) {

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            sendScheduleUpdated(seniorId);
                        }
                    }
            );

            return;
        }

        sendScheduleUpdated(seniorId);
    }

    public void notifyMedicationUpdated(Long seniorId) {

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            sendMedicationUpdated(seniorId);
                        }
                    }
            );

            return;
        }

        sendMedicationUpdated(seniorId);
    }

    private void sendHomeUpdated(Long seniorId) {
        messagingTemplate.convertAndSend(
                "/topic/senior/" + seniorId + "/home",
                "HOME_UPDATED"
        );

        log.info(
                "[WebSocket] HOME_UPDATED 전송 완료. seniorId: {}",
                seniorId
        );
    }

    private void sendScheduleUpdated(Long seniorId) {
        messagingTemplate.convertAndSend(
                "/topic/senior/" + seniorId + "/home",
                "SCHEDULE_UPDATED"
        );

        log.info(
                "[WebSocket] SCHEDULE_UPDATED 전송 완료. seniorId: {}",
                seniorId
        );
    }

    private void sendMedicationUpdated(Long seniorId) {
        messagingTemplate.convertAndSend(
                "/topic/senior/" + seniorId + "/home",
                "MEDICATION_UPDATED"
        );

        log.info(
                "[WebSocket] MEDICATION_UPDATED 전송 완료. seniorId: {}",
                seniorId
        );
    }
}