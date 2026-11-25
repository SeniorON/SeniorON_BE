package com.example.senioron.domain.senior.dto.response;

import com.example.senioron.domain.senior.entity.SeniorRelation;
import com.example.senioron.domain.senior.entity.SeniorWithdrawalNotice;
import com.example.senioron.domain.senior.entity.SeniorWithdrawalNoticeRecipient;
import java.time.LocalDateTime;

public record SeniorWithdrawalNoticeResponse(
        Long withdrawalNoticeId,
        Long familyId,
        Long seniorId,
        String seniorName,
        SeniorRelation relation,
        String customRelation,
        LocalDateTime withdrawnAt
) {

    public static SeniorWithdrawalNoticeResponse from(
            SeniorWithdrawalNoticeRecipient recipient
    ) {
        SeniorWithdrawalNotice notice = recipient.getNotice();
        return new SeniorWithdrawalNoticeResponse(
                notice.getSeniorWithdrawalNoticeId(),
                notice.getFamilyId(),
                notice.getSeniorId(),
                notice.getSeniorName(),
                recipient.getRelation(),
                recipient.getCustomRelation(),
                notice.getWithdrawnAt()
        );
    }
}
