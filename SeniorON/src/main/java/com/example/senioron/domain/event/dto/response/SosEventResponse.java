package com.example.senioron.domain.event.dto.response;

import com.example.senioron.domain.event.entity.Event;
import lombok.Builder;
import lombok.Getter;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Getter
@Builder
public class SosEventResponse {

    private Long id;
    private BigDecimal latitude;
    private BigDecimal longitude;
    @Schema(description = "SOS 접수 시 '주소 확인 중'. 비동기 조회 완료 후 이벤트 상세 API에서 주소 확인 가능")
    private String address;
    private Integer deviceBattery;

    /** 알림이 생성되어 비동기 발송을 시작한 가족 수. */
    private int receiverCount;

    public static SosEventResponse of(Event event, int receiverCount) {
        return SosEventResponse.builder()
                .id(event.getEventId())
                .latitude(event.getLatitude())
                .longitude(event.getLongitude())
                .deviceBattery(event.getDeviceBattery())
                .address(event.getAddress())
                .receiverCount(receiverCount)
                .build();
    }
}
