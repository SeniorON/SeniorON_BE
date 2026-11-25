package com.example.senioron.domain.senior.event;

import com.example.senioron.domain.senior.service.SeniorWithdrawalPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeniorWithdrawalNoticeCreatedEventListener {

    private final SeniorWithdrawalPushService pushService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(SeniorWithdrawalNoticeCreatedEvent event) {
        try {
            pushService.send(event);
        } catch (TaskRejectedException exception) {
            log.error(
                    "[SeniorWithdrawalPush] 작업 등록 실패. noticeId={}",
                    event.withdrawalNoticeId(),
                    exception
            );
        }
    }
}
