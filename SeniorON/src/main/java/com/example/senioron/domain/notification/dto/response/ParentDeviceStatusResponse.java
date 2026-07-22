package com.example.senioron.domain.notification.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ParentDeviceStatusResponse {
    private boolean online;
}
