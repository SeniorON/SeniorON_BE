package com.example.senioron.domain.device.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceStatusWebSocketService {

    public static final String UPDATED_EVENT = "DEVICE_STATUS_UPDATED";

    private final SimpMessagingTemplate messagingTemplate;

    public void notifyStatusUpdated(Long seniorId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            try {
                                sendStatusUpdated(seniorId);
                            } catch (MessagingException e) {
                                log.error(
                                        "[WebSocket] DEVICE_STATUS_UPDATED 전송 실패. seniorId: {}",
                                        seniorId,
                                        e
                                );
                            }
                        }
                    }
            );
            return;
        }

        sendStatusUpdated(seniorId);
    }

    private void sendStatusUpdated(Long seniorId) {
        messagingTemplate.convertAndSend(
                "/topic/senior/" + seniorId + "/device",
                UPDATED_EVENT
        );
        log.info("[WebSocket] DEVICE_STATUS_UPDATED 전송 완료. seniorId: {}", seniorId);
    }
}
