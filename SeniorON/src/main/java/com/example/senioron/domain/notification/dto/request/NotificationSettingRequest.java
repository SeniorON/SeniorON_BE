package com.example.senioron.domain.notification.dto.request;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class NotificationSettingRequest {
    private Boolean enabled;
}
