package com.example.senioron.domain.notification.dto.response;

import com.example.senioron.domain.notification.entity.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class NotificationSettingResponse{
    private Boolean enabled;
    private NotificationType type;

    public static NotificationSettingResponse from(NotificationType type, Boolean enabled) {
        return NotificationSettingResponse.builder()
                .type(type)
                .enabled(enabled)
                .build();
    }
}
