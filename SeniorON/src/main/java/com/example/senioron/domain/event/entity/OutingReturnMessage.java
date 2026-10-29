package com.example.senioron.domain.event.entity;

public final class OutingReturnMessage {
    private OutingReturnMessage() {
    }

    public static String resolve(OutingPhase phase, OutingDetectionType detectionType) {
        if (phase == null) {
            return "외출,귀가가 감지됨";
        }
        if (detectionType == OutingDetectionType.RECONNECTION) {
            return phase == OutingPhase.OUTING
                    ? "집 밖에 있는 것이 확인됐어요" : "집에 있는 것이 확인됐어요";
        }
        return phase == OutingPhase.OUTING ? "외출하셨어요" : "귀가하셨어요";
    }
}
