package com.example.senioron.domain.event.dto.response;

import com.example.senioron.domain.event.entity.Event;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class SosEventResponse {

    private Long id;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Integer deviceBattery;

    public static SosEventResponse of (Event event){
        return SosEventResponse.builder()
                .id(event.getEventId())
                .latitude(event.getLatitude())
                .longitude(event.getLongitude())
                .deviceBattery(event.getDeviceBattery())
                .build();
    }
}
