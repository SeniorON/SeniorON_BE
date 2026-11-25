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
public class FamilyPhotoDeletedEventListener {

    private final FamilyPhotoPushService familyPhotoPushService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(FamilyPhotoDeletedEvent event) {
        try {
            familyPhotoPushService.sendDeleted(
                    event.familyPhotoId(),
                    event.tokens()
            );
        } catch (TaskRejectedException exception) {
            log.error(
                    "[FamilyPhotoDeleted] 작업 등록 실패. familyPhotoId={}",
                    event.familyPhotoId(),
                    exception
            );
        }
    }
}