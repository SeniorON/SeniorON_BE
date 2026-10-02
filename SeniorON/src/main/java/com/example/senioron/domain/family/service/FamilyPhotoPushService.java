package com.example.senioron.domain.family.service;

import com.example.senioron.domain.event.util.FcmSender;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FamilyPhotoPushService {

    private final FamilyPhotoPushTargetService targetService;
    private final FcmSender fcmSender;

    @Async("familyPhotoPushExecutor")
    public void send(Long familyPhotoId) {
        FamilyPhotoPushTargetService.PushTarget target;

        try {
            target = targetService.load(familyPhotoId).orElse(null);
        } catch (RuntimeException exception) {
            log.error(
                    "[FamilyPhotoPush] 대상 조회 실패. familyPhotoId={}",
                    familyPhotoId,
                    exception
            );
            return;
        }

        if (target == null) {
            return;
        }

        Map<String, String> data = Map.of(
                "type", "FAMILY_PHOTO_SHARED",
                "title", "새 가족사진",
                "body", target.body(),
                "familyPhotoId", familyPhotoId.toString()
        );

        int successCount = 0;
        int failureCount = 0;

        for (String token : target.tokens()) {
            try {
                if (fcmSender.sendData(token, data)) {
                    successCount++;
                } else {
                    failureCount++;
                }
            } catch (RuntimeException exception) {
                failureCount++;
                log.error(
                        "[FamilyPhotoPush] 발송 예외. familyPhotoId={}",
                        familyPhotoId,
                        exception
                );
            }
        }

        log.info(
                "[FamilyPhotoPush] 발송 처리 종료. familyPhotoId={}, "
                        + "fcmAcceptedCount={}, failureCount={}",
                familyPhotoId,
                successCount,
                failureCount
        );
    }
}