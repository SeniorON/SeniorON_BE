package com.example.senioron.domain.home.dto.response;

import com.example.senioron.domain.home.entity.ActionType;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ButtonOptionResponse {

    @JsonProperty("option_id")
    private Long optionId;

    @JsonProperty("button_name")
    private String buttonName;

    private String icon;

    @JsonProperty("action_type")
    private ActionType actionType;

    @JsonProperty("action_value")
    private String actionValue;

    public ButtonOptionResponse(
            Long optionId,
            String buttonName,
            String icon,
            ActionType actionType,
            String actionValue
    ) {
        this.optionId = optionId;
        this.buttonName = buttonName;
        this.icon = icon;
        this.actionType = actionType;
        this.actionValue = actionValue;
    }

    public Long getOptionId() {
        return optionId;
    }

    public String getButtonName() {
        return buttonName;
    }

    public String getIcon() {
        return icon;
    }

    public ActionType getActionType() {
        return actionType;
    }

    public String getActionValue() {
        return actionValue;
    }
}