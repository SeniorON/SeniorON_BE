package com.example.senioron.domain.permission.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeniorPermissionSettingUpdateRequest {

    private Boolean locationEnabled;

    private Boolean inactivityDetectionEnabled;

    public boolean hasAnyValue() {
        return locationEnabled != null || inactivityDetectionEnabled != null;
    }
}
