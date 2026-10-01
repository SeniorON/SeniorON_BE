package com.example.senioron.domain.device.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class DeviceStatusWebSocketServiceTest {

    private final SimpMessagingTemplate messagingTemplate =
            mock(SimpMessagingTemplate.class);
    private final DeviceStatusWebSocketService service =
            new DeviceStatusWebSocketService(messagingTemplate);

    @Test
    void sendsDeviceStatusUpdatedToSeniorDeviceTopic() {
        service.notifyStatusUpdated(7L);

        verify(messagingTemplate).convertAndSend(
                "/topic/senior/7/device",
                DeviceStatusWebSocketService.UPDATED_EVENT
        );
    }

    @Test
    void sendsOnlyAfterTransactionCommit() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            service.notifyStatusUpdated(7L);

            verifyNoInteractions(messagingTemplate);
            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();
            synchronizations.forEach(TransactionSynchronization::afterCommit);

            verify(messagingTemplate).convertAndSend(
                    "/topic/senior/7/device",
                    DeviceStatusWebSocketService.UPDATED_EVENT
            );
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void doesNotSendWhenTransactionRollsBack() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            service.notifyStatusUpdated(7L);
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(synchronization -> synchronization.afterCompletion(
                            TransactionSynchronization.STATUS_ROLLED_BACK
                    ));

            verifyNoInteractions(messagingTemplate);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void doesNotPropagateSendFailureAfterTransactionCommit() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            doThrow(new MessageDeliveryException("WebSocket send failed"))
                    .when(messagingTemplate)
                    .convertAndSend(
                            "/topic/senior/7/device",
                            DeviceStatusWebSocketService.UPDATED_EVENT
                    );

            service.notifyStatusUpdated(7L);
            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();

            assertDoesNotThrow(() ->
                    synchronizations.forEach(TransactionSynchronization::afterCommit));
            verify(messagingTemplate).convertAndSend(
                    "/topic/senior/7/device",
                    DeviceStatusWebSocketService.UPDATED_EVENT
            );
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }
}
