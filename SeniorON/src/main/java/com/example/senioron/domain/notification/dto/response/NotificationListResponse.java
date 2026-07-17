package com.example.senioron.domain.notification.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class NotificationListResponse {
    private long totalCount;
    private List<NotificationItem> items;
    private Long nextCursor;

    @Getter
    @Builder
    public static class NotificationItem {
        private Long notificationId;
        private Long eventId;
        private String title;
        private String summary;
        private LocalDateTime occurredAt;
        private boolean isRead;
    }
}
