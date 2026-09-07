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

    private void sendHomeUpdated(Long seniorId) {
        messagingTemplate.convertAndSend(
                "/topic/senior/" + seniorId + "/home",
                "HOME_UPDATED"
        );
    }
}