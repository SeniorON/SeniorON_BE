package com.example.senioron.domain.notification.dto.response;

import com.example.senioron.domain.notification.entity.NotificationSettingType;
import com.example.senioron.domain.notification.entity.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class NotificationSettingResponse{
    private Boolean enabled;
    private NotificationSettingType type;

    public static NotificationSettingResponse from(NotificationSettingType type, Boolean enabled) {
        return NotificationSettingResponse.builder()
                .type(type)
                .enabled(enabled)
                .build();
    }
}
