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
