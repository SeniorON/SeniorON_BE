package com.example.senioron.domain.senior.dto.response;

import com.example.senioron.domain.senior.entity.SeniorRelation;
import com.example.senioron.domain.senior.entity.SeniorWithdrawalNotice;
import com.example.senioron.domain.senior.entity.SeniorWithdrawalNoticeRecipient;

public record ManagedSeniorResponse(
        Long familyId,
        Long seniorId,
        String seniorName,
        SeniorRelation relation,
        String customRelation,
        boolean seniorProfileCompleted,
        boolean withdrawn,
        Long withdrawalNoticeId
) {

    public ManagedSeniorResponse(
            Long familyId,
            Long seniorId,
            String seniorName,
            SeniorRelation relation,
            String customRelation,
            boolean seniorProfileCompleted
    ) {
        this(
                familyId,
                seniorId,
                seniorName,
                relation,
                customRelation,
                seniorProfileCompleted,
                false,
                null
        );
    }

    public static ManagedSeniorResponse withdrawn(
            SeniorWithdrawalNoticeRecipient recipient
    ) {
        SeniorWithdrawalNotice notice = recipient.getNotice();
        return new ManagedSeniorResponse(
                notice.getFamilyId(),
                notice.getSeniorId(),
                notice.getSeniorName(),
                recipient.getRelation(),
                recipient.getCustomRelation(),
                true,
                true,
                notice.getSeniorWithdrawalNoticeId()
        );
    }
}
