package com.example.senioron.domain.senior.event;

import java.util.List;

public record SeniorWithdrawalNoticeCreatedEvent(
        Long withdrawalNoticeId,
        Long seniorId,
        Long familyId,
        String seniorName,
        List<PushTarget> targets
) {

    public SeniorWithdrawalNoticeCreatedEvent {
        if (withdrawalNoticeId == null || familyId == null) {
            throw new IllegalArgumentException("탈퇴 알림에는 noticeId와 familyId가 필요합니다.");
        }
        targets = List.copyOf(targets);
    }

    public record PushTarget(
            Long recipientUserId,
            List<String> tokens
    ) {

        public PushTarget {
            tokens = List.copyOf(tokens);
        }
    }
}
