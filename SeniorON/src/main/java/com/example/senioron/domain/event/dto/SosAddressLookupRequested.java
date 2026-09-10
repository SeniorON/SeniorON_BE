package com.example.senioron.domain.event.dto;

import java.math.BigDecimal;

// 비동기 작업에는 엔티티 대신 커밋된 이벤트의 ID와 좌표만 전달한다.
public record SosAddressLookupRequested(Long eventId, BigDecimal latitude, BigDecimal longitude) {
}
