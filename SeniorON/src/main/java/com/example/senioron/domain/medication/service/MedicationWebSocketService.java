package com.example.senioron.domain.medication.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class MedicationWebSocketService {

    private final SimpMessagingTemplate messagingTemplate;

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

    private void sendMedicationUpdated(Long seniorId) {
        messagingTemplate.convertAndSend(
                "/topic/senior/" + seniorId + "/medication",
                "MEDICATION_UPDATED"
        );

        log.info(
                "[WebSocket] 복약 MEDICATION_UPDATED 전송 완료. seniorId: {}",
                seniorId
        );
    }
}