package com.example.senioron.domain.companion.entity;

public enum TurnStatus {
    // 현재 단계
    RECEIVED, // 턴이 생성됨
    TRANSCRIBED, // 음성 입력을 STT 텍스트로 변환함
    RESPONSE_GENERATED, // LLM 또는 안전 규칙 응답을 생성함
    COMPLETED, // TTS까지 완료함
    FAILED // 처리 중 실패함
}
