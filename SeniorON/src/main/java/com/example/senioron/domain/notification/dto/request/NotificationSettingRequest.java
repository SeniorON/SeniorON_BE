package com.example.senioron.domain.notification.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;


@Getter
public class NotificationSettingRequest {
    @NotNull
    private Boolean enabled;

    @Builder
    public NotificationSettingRequest(Boolean enabled) {
        this.enabled = enabled;
    }
}
