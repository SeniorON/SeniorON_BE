package com.example.senioron.domain.senior.service;

import com.example.senioron.domain.event.util.FcmSender;
import com.example.senioron.domain.senior.event.SeniorWithdrawalNoticeCreatedEvent;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeniorWithdrawalPushService {

    private static final String TITLE = "시니어 탈퇴 안내";

    private final FcmSender fcmSender;

    @Async("seniorWithdrawalPushExecutor")
    public void send(SeniorWithdrawalNoticeCreatedEvent event) {
        String body = event.seniorName()
                + "님이 시니어온 서비스를 탈퇴하셨습니다. 앱에서 탈퇴 안내를 확인해 주세요.";
        Map<String, String> data = Map.of(
                "type", "SENIOR_WITHDRAWAL",
                "title", TITLE,
                "body", body,
                "withdrawalNoticeId", event.withdrawalNoticeId().toString(),
                "seniorId", event.seniorId().toString(),
                "familyId", event.familyId().toString()
        );

        int successCount = 0;
        int failureCount = 0;

        for (SeniorWithdrawalNoticeCreatedEvent.PushTarget target : event.targets()) {
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
                            "[SeniorWithdrawalPush] 발송 예외. noticeId={}, recipientUserId={}",
                            event.withdrawalNoticeId(),
                            target.recipientUserId(),
                            exception
                    );
                }
            }
        }

        log.info(
                "[SeniorWithdrawalPush] 발송 종료. noticeId={}, successCount={}, failureCount={}",
                event.withdrawalNoticeId(),
                successCount,
                failureCount
        );
    }
}
