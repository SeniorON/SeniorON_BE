package com.example.senioron.domain.companion.service.model;

import com.example.senioron.domain.companion.entity.SafetyType;

public record SafetyDecision(
        SafetyType type,
        String ruleId
) {

    public SafetyDecision {
        if (type == null) {
            throw new IllegalArgumentException("안전 판단 결과는 필수입니다.");
        }

        if (type == SafetyType.EMERGENCY
                && (ruleId == null
                || ruleId.isBlank())) {

            throw new IllegalArgumentException("위험 판단 근거 ID는 필수입니다.");
        }
    }

    public static SafetyDecision normal() {
        return new SafetyDecision(
                SafetyType.NORMAL,
                null
        );
    }

    public static SafetyDecision emergency(
            String ruleId
    ) {
        return new SafetyDecision(
                SafetyType.EMERGENCY,
                ruleId
        );
    }

    public boolean isEmergency() {
        return type == SafetyType.EMERGENCY;
    }
}