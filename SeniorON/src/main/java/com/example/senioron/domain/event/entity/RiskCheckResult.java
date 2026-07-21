package com.example.senioron.domain.event.entity;

public enum RiskCheckResult {
    SAFE,        // 검사 완료, 안전 확인
    DANGEROUS,   // 검사 완료, 위험 확인
    UNAVAILABLE  // 검사 실패 (API 오류, 타임아웃 등) — "모름" 상태
}