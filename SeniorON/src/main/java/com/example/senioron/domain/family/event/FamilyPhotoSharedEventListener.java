package com.example.senioron.domain.family.event;

import com.example.senioron.domain.family.service.FamilyPhotoPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class FamilyPhotoSharedEventListener {

    private final FamilyPhotoPushService familyPhotoPushService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(FamilyPhotoSharedEvent event) {
        try {
            familyPhotoPushService.send(event.familyPhotoId());
        } catch (TaskRejectedException exception) {
            log.error(
                    "[FamilyPhotoPush] 작업 등록 실패. familyPhotoId={}",
                    event.familyPhotoId(),
                    exception
            );
        }
    }
}