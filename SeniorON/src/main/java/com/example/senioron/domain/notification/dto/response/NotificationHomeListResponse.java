package com.example.senioron.domain.notification.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class NotificationHomeListResponse {
    private long enabledCount;  //받고 있는 알람 갯수
    private List<NotificationHomeResponse> items;

    public static NotificationHomeListResponse of(List<NotificationHomeResponse> items) {
        long enabledCount = items.stream().filter(NotificationHomeResponse::isEnabled).count();
        return NotificationHomeListResponse.builder()
                .enabledCount(enabledCount)
                .items(items)
                .build();
    }
}
