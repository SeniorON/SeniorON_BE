package com.example.senioron.domain.home.dto.request;

import com.example.senioron.domain.home.entity.ActionType;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class HomeButtonUpdateRequest {

    private List<ButtonRequest> buttons;

    public List<ButtonRequest> getButtons() {
        return buttons;
    }

    public static class ButtonRequest {

        @JsonProperty("button_id")
        private Long buttonId;

        @JsonProperty("button_order")
        private Integer buttonOrder;

        @JsonProperty("button_name")
        private String buttonName;

        private String icon;

        @JsonProperty("action_type")
        private ActionType actionType;

        @JsonProperty("action_value")
        private String actionValue;

        public Long getButtonId() {
            return buttonId;
        }

        public Integer getButtonOrder() {
            return buttonOrder;
        }

        public String getButtonName() {
            return buttonName;
        }

        public String getIcon() {
            return icon;
        }
    }
}