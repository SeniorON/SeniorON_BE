package com.example.senioron.domain.home.dto.request;

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