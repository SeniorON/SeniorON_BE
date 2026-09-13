package com.example.senioron.domain.home.service;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class HomeWebSocketService {

    private final SimpMessagingTemplate messagingTemplate;

    public void notifyHomeUpdated(Long seniorId) {
        notifyAfterCommit(
                seniorId,
                "HOME_UPDATED"
        );
    }

    public void notifyScheduleUpdated(Long seniorId) {
        notifyAfterCommit(
                seniorId,
                "SCHEDULE_UPDATED"
        );
    }

    public void notifyMedicationUpdated(Long seniorId) {
        notifyAfterCommit(
                seniorId,
                "MEDICATION_UPDATED"
        );
    }

    private void notifyAfterCommit(
            Long seniorId,
            String message
    ) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {

            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            sendUpdated(
                                    seniorId,
                                    message
                            );
                        }
                    }
            );

            return;
        }

        sendUpdated(
                seniorId,
                message
        );
    }

    private void sendUpdated(
            Long seniorId,
            String message
    ) {
        messagingTemplate.convertAndSend(
                "/topic/senior/" + seniorId + "/home",
                message
        );
    }
}