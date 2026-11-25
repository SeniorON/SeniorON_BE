package com.example.senioron.domain.inactivity.dto.response;

import com.example.senioron.domain.inactivity.entity.InactivitySetting;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class InactivitySettingResponse {
    private Long usersId;
    private Integer thresholdHours;
    private Boolean isEnabled;

    public static InactivitySettingResponse from(InactivitySetting setting) {
        return InactivitySettingResponse.builder()
                .usersId(setting.getUserId())
                .thresholdHours(setting.getThresholdHours())
                .isEnabled(setting.getIsEnabled())
                .build();
    }
}
