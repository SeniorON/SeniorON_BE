package com.example.senioron.domain.event.dto.response;

import com.example.senioron.domain.event.entity.Event;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class InactivityResponse {
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private LocalDateTime LastSeenAt;

    public static InactivityResponse of(Event event) {
        return InactivityResponse.builder()
                .address(event.getAddress())
                .latitude(event.getLatitude())
                .longitude(event.getLongitude())
                .build();
    }
}
