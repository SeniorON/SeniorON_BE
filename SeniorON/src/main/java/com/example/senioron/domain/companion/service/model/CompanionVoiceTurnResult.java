package com.example.senioron.domain.companion.service.model;

import com.example.senioron.domain.companion.dto.response.CompanionVoiceTurnResponse;
import com.example.senioron.global.apiPayload.code.BaseCode;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.code.ResultCode;

public record CompanionVoiceTurnResult(
        BaseCode code,
        boolean success,
        CompanionVoiceTurnResponse response
) {

    public static CompanionVoiceTurnResult success(
            CompanionVoiceTurnResponse response
    ) {
        return new CompanionVoiceTurnResult(
                ResultCode.OK,
                true,
                response
        );
    }

    public static CompanionVoiceTurnResult processing(
            CompanionVoiceTurnResponse response
    ) {
        return new CompanionVoiceTurnResult(
                ResultCode.ACCEPTED,
                true,
                response
        );
    }

    public static CompanionVoiceTurnResult failure(
            ErrorCode errorCode,
            CompanionVoiceTurnResponse response
    ) {
        return new CompanionVoiceTurnResult(
                errorCode,
                false,
                response
        );
    }
}