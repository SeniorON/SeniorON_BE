package com.example.senioron.domain.event.dto.response;

import com.example.senioron.domain.event.entity.Event;
import com.example.senioron.domain.notification.dto.NotificationDispatchResult;
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

    /**
     * 알림 대상 가족 수. 0이면 가족이나 자녀가 등록되지 않아 아무에게도 알리지 못한 것이므로,
     * 앱은 직접 연락하도록 안내해야 한다.
     */
    private int receiverCount;

    /**
     * 그중 푸시 발송에 성공한 수. 0이면 SOS가 가족에게 전달되지 못했다.
     * 발송 성공이 단말 도달까지 보장하지는 않는다.
     */
    private int notifiedCount;

    public static SosEventResponse of (Event event, NotificationDispatchResult dispatchResult){
        return SosEventResponse.builder()
                .id(event.getEventId())
                .latitude(event.getLatitude())
                .longitude(event.getLongitude())
                .deviceBattery(event.getDeviceBattery())
                .address(event.getAddress())
                .receiverCount(dispatchResult.receiverCount())
                .notifiedCount(dispatchResult.notifiedCount())
                .build();
    }
}
