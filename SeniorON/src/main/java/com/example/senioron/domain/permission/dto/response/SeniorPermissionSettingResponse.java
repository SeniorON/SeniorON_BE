package com.example.senioron.domain.permission.dto.response;

import com.example.senioron.domain.permission.entity.SeniorPermissionSetting;

public record SeniorPermissionSettingResponse(
        Long seniorId,
        Boolean locationEnabled,
        Boolean inactivityDetectionEnabled
) {

    public static SeniorPermissionSettingResponse from(SeniorPermissionSetting setting) {
        return new SeniorPermissionSettingResponse(
                setting.getSeniorId(),
                setting.getLocationEnabled(),
                setting.getInactivityDetectionEnabled()
        );
    }
}
